package ai.pleos.plugrest.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.ErrorCode
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.control.MoveControl
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.EvChargePortOpenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Pleos Vehicle SDK 2.3.2 어댑터. 시그니처 근거: docs/research/05-sdk-signatures.md
 * 모든 SDK 호출은 onFailed로 실패를 알리며, 미지원(NOT_SUPPORTED_*)·권한 오류는 해당 Feature만 건너뛴다.
 */
class PleosVehicleGateway(context: Context) : VehicleGateway {
    private val appContext = context.applicationContext
    private var vehicle = Vehicle(appContext)
    private var released = false
    private var refreshJob: kotlinx.coroutines.Job? = null
    /** checkXxxCapabilities 결과 캐시: 앰비언트 밝기 허용 범위(모르면 null → clamp 안 함) */
    @Volatile private var ambientRange: IntRange? = null
    private val _state = MutableStateFlow(VehicleState())
    override val state: StateFlow<VehicleState> = _state
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val driver = SeatArea(SeatValue.ROW_1_LEFT)
    private val rearSeats = listOf(SeatValue.ROW_2_LEFT, SeatValue.ROW_2_RIGHT)
    private val windows = mapOf(
        WindowValue.ROW_1_RIGHT to "조수석", WindowValue.ROW_2_LEFT to "뒷좌석 왼쪽",
        WindowValue.ROW_2_RIGHT to "뒷좌석 오른쪽", WindowValue.ROW_1_LEFT to "운전석",
    )
    private val windowPos = mutableMapOf<WindowValue, Int>()
    private val rearOccupied = mutableMapOf<SeatValue, Boolean>()
    private var car: Car? = null
    private var ux: CarUxRestrictionsManager? = null
    @Volatile private var seatMoving = false
    /** 실제로 등받이를 젖힌 시간(ms). 복원은 이만큼만, 1회만. */
    @Volatile private var reclinedMs = 0L
    private val listeners = mutableListOf<() -> Unit>() // unregister 호출 모음

    // ---- lifecycle ----------------------------------------------------------------------------

    override fun start() {
        if (_state.value.connected) return
        if (released) { vehicle = Vehicle(appContext); released = false } // release 후엔 새 객체로
        try {
            vehicle.initialize()
            _state.update { it.copy(connected = true) }
        } catch (e: Exception) {
            Log.e(TAG, "Vehicle.initialize failed", e)
            return
        }
        registerListeners()
        registerUxRestrictions()
        refresh()
        scope.launch { checkCapabilities() }
    }

    /**
     * 초기화 시 1회: Seat 등받이 이동·Light 앰비언트(밝기·색)의 지원 여부와 범위를 미리 확인한다.
     * 미지원이면 set을 보내지 않고 바로 "이 차량 미지원". onFailed 건너뛰기 경로는 그대로 두어 이중 안전.
     */
    private suspend fun checkCapabilities() {
        val seat = vehicle.getSeat(); val light = vehicle.getLight()
        get<Pair<CapabilityConfig, List<SeatArea>>>(Feature.SEAT_RECLINE) { ok, err ->
            seat.checkMoveSeatBackrestAngleCapabilities({ c, areas -> ok(c to areas) }, err)
        }?.let { (c, areas) -> if (!c.writable() || areas.none { it.isDriver() }) markUnsupported(Feature.SEAT_RECLINE) }
        get<Pair<CapabilityConfig, Map<SeatArea, AreaBounds<Int>>>>(Feature.AMBIENT) { ok, err ->
            light.checkAmbientLightBrightnessCapabilities({ c, m -> ok(c to m) }, err)
        }?.let { (c, bounds) ->
            val b = bounds.entries.firstOrNull { it.key.isDriver() }?.value
            if (!c.writable() || b == null) markUnsupported(Feature.AMBIENT) else ambientRange = b.minValue..b.maxValue
        }
        get<Pair<CapabilityConfig, List<SeatArea>>>(Feature.AMBIENT) { ok, err ->
            light.checkAmbientLightBrightnessColorCapabilities({ c, areas -> ok(c to areas) }, err)
        }?.let { (c, areas) -> if (!c.writable() || areas.none { it.isDriver() }) markUnsupported(Feature.AMBIENT) }
    }

    private fun CapabilityConfig.writable() = accessType == AccessType.WRITE || accessType == AccessType.READ_WRITE
    private fun SeatArea.isDriver() = values.contentEquals(driver.values) // SeatArea는 data class가 아니라 값 배열로 비교
    private fun markUnsupported(f: Feature) = _state.update { it.copy(unsupported = it.unsupported + f) }

    override fun stop() {
        refreshJob?.cancel() // 이전 vehicle 객체로 get이 계속 돌지 않게 (검수 C2)
        listeners.forEach { u -> try { u() } catch (e: Exception) { Log.w(TAG, "unregister", e) } }
        listeners.clear()
        try { ux?.unregisterListener(); car?.disconnect() } catch (_: Throwable) {}
        try { vehicle.release() } catch (e: Exception) { Log.w(TAG, "release", e) }
        released = true
        _state.update { it.copy(connected = false) }
    }

    override fun refresh() {
        refreshJob = scope.launch {
            val ev = vehicle.getEvBattery()
            get<Int>(null) { ok, err -> ev.getEvBatteryStateOfCharge(ok, err) }?.let { soc -> _state.update { it.copy(soc = soc) } }
            get<Boolean>(Feature.CHARGE_PORT) { ok, err -> ev.isEvChargePortOpen(ok, err) }?.let { p -> _state.update { it.copy(chargePortConnected = p) } }
            get<Boolean>(null) { ok, err -> ev.getEvChargeSwitchState(ok, err) }?.let { sw -> _state.update { it.copy(chargeSwitchOn = sw) } }
            get<VehicleGear>(Feature.GEAR) { ok, err -> vehicle.getGear().getCurrentGearSelectionState(ok, err) }?.let { g -> _state.update { it.copy(gear = g.toGear()) } }
            val seat = vehicle.getSeat()
            rearSeats.forEach { s -> get<Boolean>(Feature.OCCUPANCY) { ok, err -> seat.getCurrentSeatOccupancy(SeatArea(s), ok, err) }?.let { rearOccupied[s] = it } }
            _state.update { it.copy(rearSeatOccupied = rearOccupancy()) }
        }
    }

    private fun registerListeners() {
        val ev = vehicle.getEvBattery()
        val socL = object : EvBatteryStateOfChargeListener {
            override fun onEvBatteryStateOfChargeUpdated(value: Int?) { value?.let { v -> _state.update { it.copy(soc = v) } } }
            override fun onFailed(e: Exception) = fail(null, e)
        }
        ev.registerEvBatteryStateOfCharge(socL); listeners += { ev.unregisterEvBatteryStateOfCharge(socL) }
        val portL = object : EvChargePortOpenListener {
            override fun onEvChargePortOpenUpdated(value: Boolean?) { value?.let { v -> _state.update { it.copy(chargePortConnected = v) } } }
            override fun onFailed(e: Exception) = fail(Feature.CHARGE_PORT, e)
        }
        ev.registerEvChargePortOpen(portL); listeners += { ev.unregisterEvChargePortOpen(portL) }
        val gear = vehicle.getGear()
        val gearL = object : GearSelectionListener {
            override fun onSelected(gear: VehicleGear?) { _state.update { it.copy(gear = gear?.toGear()) } }
            override fun onFailed(e: Exception) = fail(Feature.GEAR, e)
        }
        gear.registerGearSelection(gearL); listeners += { gear.unregisterGearSelection(gearL) }
        val brake = vehicle.getBrake()
        val brakeL = object : BrakePedalStatusListener {
            override fun onBrakePedalStatusUpdated(value: Boolean?) { _state.update { it.copy(brakePressed = value == true) } }
            override fun onFailed(e: Exception) = fail(Feature.BRAKE, e)
        }
        brake.registerBrakePedalStatus(brakeL); listeners += { brake.unregisterBrakePedalStatus(brakeL) }
        val light = vehicle.getLight()
        val cabinL = object : LightStateListener {
            override fun onLightStateUpdated(value: LightState?) { _state.update { it.copy(cabinLightOn = value == LightState.ON) } }
            override fun onFailed(e: Exception) = fail(Feature.CABIN_LIGHT, e)
        }
        light.registerCabinLightState(cabinL); listeners += { light.unregisterCabinLightState(cabinL) }
        val window = vehicle.getWindow()
        windows.keys.forEach { w ->
            val area = WindowArea(w)
            val l = object : WindowPositionListener {
                override fun onWindowPositionUpdated(value: Int?) {
                    windowPos[w] = value ?: 0
                    _state.update { it.copy(openWindows = windows.filter { (k, _) -> (windowPos[k] ?: 0) > 0 }.values.toList()) }
                }
                override fun onFailed(e: Exception) = fail(Feature.WINDOW, e)
            }
            window.registerWindowPosition(area, l); listeners += { window.unregisterWindowPosition(area, l) }
        }
        val seat = vehicle.getSeat()
        rearSeats.forEach { s ->
            val area = SeatArea(s)
            val l = object : SeatOccupancyListener {
                override fun onSeatOccupancyUpdated(value: Boolean?) {
                    rearOccupied[s] = value == true
                    _state.update { it.copy(rearSeatOccupied = rearOccupancy()) }
                }
                override fun onFailed(e: Exception) = fail(Feature.OCCUPANCY, e)
            }
            seat.registerSeatOccupancy(area, l); listeners += { seat.unregisterSeatOccupancy(area, l) }
        }
    }

    private fun registerUxRestrictions() {
        // ponytail: android.car는 AAOS 이미지에만 있음. 없으면(일반 AVD) 제한 없음으로 둔다.
        try {
            val c = Car.createCar(appContext) ?: return
            car = c
            val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager ?: return
            ux = m
            m.registerListener { r -> _state.update { it.copy(uxRestricted = r.isRequiresDistractionOptimization) } }
            m.currentCarUxRestrictions?.let { r -> _state.update { it.copy(uxRestricted = r.isRequiresDistractionOptimization) } }
        } catch (t: Throwable) {
            Log.w(TAG, "CarUxRestrictions unavailable: $t")
        }
    }

    // ---- snapshot / apply / restore ----------------------------------------------------------

    override suspend fun readSnapshot(): CabinSnapshot {
        val seat = vehicle.getSeat(); val light = vehicle.getLight(); val hvac = vehicle.getHVAC()
        return CabinSnapshot(
            backrestAngle = get(Feature.SEAT_RECLINE) { ok, err -> seat.getCurrentSeatBackrestAnglePosition(driver, ok, err) },
            seatVent = get(Feature.SEAT_VENT) { ok, err -> seat.getHvacSeatVentilationState(driver, ok, err) },
            ambientBrightness = get(Feature.AMBIENT) { ok, err -> light.getAmbientLightBrightness(driver, ok, err) },
            ambientColor = get(Feature.AMBIENT) { ok, err -> light.getAmbientLightBrightnessColor(driver, ok, err) },
            cabinLightOn = get<LightSwitch>(Feature.CABIN_LIGHT) { ok, err -> light.getCabinLightSwitch(ok, err) }?.let { it == LightSwitch.ON },
            hvacTemp = get(Feature.HVAC) { ok, err -> hvac.getHvacTemperature(driver, TemperatureUnit.CELSIUS, ok, err) },
            hvacFan = get(Feature.HVAC) { ok, err -> hvac.getHvacFanSpeed(driver, ok, err) },
            hvacPower = get(Feature.HVAC) { ok, err -> hvac.getHvacPowerState(driver, ok, err) },
            hvacRecirculation = get(Feature.HVAC) { ok, err -> hvac.getHvacRecirculationState(driver, ok, err) },
        )
    }

    override fun ambientLevel(pct: Int): Int = ambientFromPct(pct, ambientRange)

    override suspend fun setAmbientBrightness(value: Int) {
        val light = vehicle.getLight()
        set(Feature.AMBIENT) { ok, err -> light.setAmbientLightBrightness(driver, clampTo(value, ambientRange), { _, _ -> ok(Unit) }, err) }
    }

    override suspend fun setHvacFan(level: Int) {
        val hvac = vehicle.getHVAC()
        set(Feature.HVAC) { ok, err -> hvac.setHvacFanSpeed(driver, level, { _, _ -> ok(Unit) }, err) }
    }

    override suspend fun applyRest(seatMode: SeatMode, halfRecline: Boolean) {
        val seat = vehicle.getSeat(); val light = vehicle.getLight(); val hvac = vehicle.getHVAC()
        // 조명: 앰비언트 어둡게·따뜻하게, 실내등 OFF
        set(Feature.AMBIENT) { ok, err -> light.setAmbientLightBrightness(driver, ambientLevel(AMBIENT_DIM_PCT), { _, _ -> ok(Unit) }, err) }
        set(Feature.AMBIENT) { ok, err -> light.setAmbientLightBrightnessColor(driver, AMBIENT_WARM, { _, _ -> ok(Unit) }, err) }
        set(Feature.CABIN_LIGHT) { ok, err -> light.setCabinLightSwitch(LightSwitch.OFF, { ok(Unit) }, err) }
        // 공조: 22°C, 풍량 1단, 외기 유입, 전원 ON
        set(Feature.HVAC) { ok, err -> hvac.setHvacPower(driver, true, { _, _ -> ok(Unit) }, err) }
        set(Feature.HVAC) { ok, err -> hvac.setHvacTemperature(driver, REST_TEMP_C, TemperatureUnit.CELSIUS, { _, _ -> ok(Unit) }, err) }
        set(Feature.HVAC) { ok, err -> hvac.setHvacFanSpeed(driver, REST_FAN, { _, _ -> ok(Unit) }, err) }
        set(Feature.HVAC) { ok, err -> hvac.setHvacRecirculation(driver, false, { _, _ -> ok(Unit) }, err) }
        // 시트: 통풍(칩 선택), 등받이 리클라인
        when (seatMode) {
            SeatMode.VENT -> set(Feature.SEAT_VENT) { ok, err -> seat.setHvacSeatVentilation(driver, SEAT_LEVEL, { _, _ -> ok(Unit) }, err) }
            SeatMode.NONE -> Unit
        }
        moveBackrest(MoveControl.BACKWARD, if (halfRecline) RECLINE_MS / 2 else RECLINE_MS)
    }

    override suspend fun restore(snapshot: CabinSnapshot) {
        val light = vehicle.getLight(); val hvac = vehicle.getHVAC(); val seat = vehicle.getSeat()
        restoreSeat(snapshot)
        snapshot.seatVent?.let { v -> set(Feature.SEAT_VENT) { ok, err -> seat.setHvacSeatVentilation(driver, v, { _, _ -> ok(Unit) }, err) } }
        snapshot.ambientBrightness?.let { v -> set(Feature.AMBIENT) { ok, err -> light.setAmbientLightBrightness(driver, clampTo(v, ambientRange), { _, _ -> ok(Unit) }, err) } }
        snapshot.ambientColor?.let { v -> set(Feature.AMBIENT) { ok, err -> light.setAmbientLightBrightnessColor(driver, v, { _, _ -> ok(Unit) }, err) } }
        snapshot.cabinLightOn?.let { on -> set(Feature.CABIN_LIGHT) { ok, err -> light.setCabinLightSwitch(if (on) LightSwitch.ON else LightSwitch.OFF, { ok(Unit) }, err) } }
        snapshot.hvacTemp?.let { v -> set(Feature.HVAC) { ok, err -> hvac.setHvacTemperature(driver, v, TemperatureUnit.CELSIUS, { _, _ -> ok(Unit) }, err) } }
        snapshot.hvacFan?.let { v -> set(Feature.HVAC) { ok, err -> hvac.setHvacFanSpeed(driver, v, { _, _ -> ok(Unit) }, err) } }
        snapshot.hvacRecirculation?.let { v -> set(Feature.HVAC) { ok, err -> hvac.setHvacRecirculation(driver, v, { _, _ -> ok(Unit) }, err) } }
        snapshot.hvacPower?.let { v -> set(Feature.HVAC) { ok, err -> hvac.setHvacPower(driver, v, { _, _ -> ok(Unit) }, err) } }
    }

    override suspend fun restoreSeat(snapshot: CabinSnapshot) {
        // ponytail: 실제 젖힌 시간만큼만 되돌리는 시간 기반 대칭 이동(멱등). 에뮬레이터에서 각도 반영이 확인되면
        // snapshot.backrestAngle 도달 시 STOP으로 교체.
        val ms = reclinedMs
        if (ms <= 0) return
        moveBackrest(MoveControl.FORWARD, ms) // 실제 이동한 만큼만 finally에서 차감 → 취소돼도 남은 거리 보존
    }

    override fun stopSeatMotion() {
        if (!seatMoving) return
        seatMoving = false
        vehicle.getSeat().moveSeatBackrestAngle(driver, MoveControl.STOP, { _, _ -> }, { Log.w(TAG, "seat stop", it) })
        _state.update { it.copy(seatMoving = false) }
    }

    private suspend fun moveBackrest(direction: MoveControl, durationMs: Long) {
        if (Feature.SEAT_RECLINE in _state.value.unsupported) return
        val seat = vehicle.getSeat()
        val started = set(Feature.SEAT_RECLINE) { ok, err -> seat.moveSeatBackrestAngle(driver, direction, { _, _ -> ok(Unit) }, err) }
        if (!started) return
        seatMoving = true
        _state.update { it.copy(seatMoving = true) }
        val t0 = System.currentTimeMillis()
        try {
            while (seatMoving && System.currentTimeMillis() - t0 < durationMs) delay(100)
        } finally {
            // 코루틴 취소(주행 제한 진입 등)에도 모터는 반드시 정지
            withContext(NonCancellable) {
                val moved = System.currentTimeMillis() - t0
                reclinedMs = if (direction == MoveControl.BACKWARD) reclinedMs + moved else (reclinedMs - moved).coerceAtLeast(0)
                if (seatMoving) stopSeatMotion()
            }
        }
    }

    // ---- helpers ------------------------------------------------------------------------------

    private suspend fun <T> get(feature: Feature?, block: ((T?) -> Unit, (Exception) -> Unit) -> Unit): T? =
        withTimeoutOrNull(CALL_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                try {
                    block({ v -> if (cont.isActive) cont.resume(v) }, { e -> fail(feature, e); if (cont.isActive) cont.resume(null) })
                } catch (e: Exception) { fail(feature, e); if (cont.isActive) cont.resume(null) }
            }
        }

    /** 주행 제한 중에는 어떤 set도 보내지 않는다. */
    private suspend fun set(feature: Feature, block: ((Unit?) -> Unit, (Exception) -> Unit) -> Unit): Boolean {
        val s = _state.value
        if (feature in s.unsupported || s.drivingRestricted) return false
        return get(feature, block) != null
    }

    private fun fail(feature: Feature?, e: Exception) {
        val code = e.vehicleErrorCode()
        Log.w(TAG, "${feature ?: "-"} failed: ${code ?: e}")
        if (feature != null && code in SKIP_CODES) _state.update { it.copy(unsupported = it.unsupported + feature) }
    }

    /** 운전석 뒤(ROW_2_LEFT)를 못 읽었으면 null → 호출자가 보수적으로(절반 리클라인) 처리 */
    private fun rearOccupancy(): Boolean? =
        if (SeatValue.ROW_2_LEFT !in rearOccupied) null else rearOccupied.values.any { it }

    private fun VehicleGear.toGear() = when (this) {
        VehicleGear.GEAR_PARK -> Gear.PARK
        VehicleGear.GEAR_REVERSE -> Gear.REVERSE
        VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
        VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
        else -> Gear.DRIVE // GEAR_DRIVE, GEAR_1..9
    }

    companion object {
        private const val TAG = "PleosVehicle"
        private const val CALL_TIMEOUT_MS = 3_000L
        // ponytail: 실차/에뮬레이터 값 확인 후 교정하는 상수들
        const val AMBIENT_DIM_PCT = 10        // 어둡게 = 범위 최소 + 10% (검수 I2, PM). 범위 미확인이면 0~100 가정
        const val AMBIENT_WARM = 0xFFFFB36B.toInt() // ARGB 따뜻한 주황
        const val REST_TEMP_C = 22f
        const val REST_FAN = 1
        const val SEAT_LEVEL = 2
        const val RECLINE_MS = 3_000L
        private val SKIP_CODES = setOf(
            ErrorCode.ERROR_NOT_SUPPORTED_PROPERTY, ErrorCode.ERROR_NOT_SUPPORTED_AREA,
            ErrorCode.ERROR_NOT_SUPPORTED_VALUE_TYPE, ErrorCode.ERROR_SECURITY_PERMISSION,
        )

        /** Pleos Vehicle 서비스가 설치된 환경인지 (없으면 Fake 모드). */
        fun isAvailable(context: Context): Boolean =
            context.packageManager.resolveService(android.content.Intent("ai.pleos.playground.vehicle.VEHICLE_START"), 0) != null
    }
}

// ponytail: 2.3.2 AAR에서 VehicleException이 난독화(callback.exception.a)돼 `is VehicleException` 불가.
// message == ErrorCode.name 이라 문자열로 복원. SDK가 고쳐지면 (e as? VehicleException)?.errorCode 로 교체.
fun Exception.vehicleErrorCode(): ErrorCode? = message?.let { m -> ErrorCode.entries.firstOrNull { it.name == m } }
