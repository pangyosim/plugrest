package ai.pleos.plugrest.vehicle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 일반 AVD·단위테스트·2차 PT용. 기어 P, 포트 연결, SOC가 [socIntervalMs]마다 1%씩 오른다(기본 1%/분).
 * 화면 하단 패널(FakePanel)에서 SOC/포트/기어/착좌를 조작하고, set 호출 로그를 본다.
 */
class FakeVehicleGateway(
    startSoc: Int = 50,
    private val socIntervalMs: Long = 60_000L, // 1%/분 ≈ 급속 50kW. 데모는 패널 슬라이더로 당김
) : VehicleGateway {
    private val _state = MutableStateFlow(
        VehicleState(
            connected = true, soc = startSoc, chargePortConnected = true, chargeSwitchOn = true, gear = Gear.PARK, rearSeatOccupied = false,
            openWindows = emptyList(),
        )
    )
    override val state: StateFlow<VehicleState> = _state
    private val _log = MutableStateFlow<List<String>>(emptyList())
    /** "Seat.moveSeatBackrestAngle(ROW_1_LEFT, BACKWARD)" 식의 set 호출 기록 (최근 것이 앞). */
    val log: StateFlow<List<String>> = _log
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null
    private var seatJob: Job? = null
    @Volatile private var reclinedMs = 0L

    // ---- 패널 조작 ----
    fun setSoc(v: Int) = _state.update { it.copy(soc = v.coerceIn(0, 100)) }
    fun setPortConnected(v: Boolean) = _state.update { it.copy(chargePortConnected = v, chargeSwitchOn = v) }
    fun setGear(g: Gear) = _state.update { it.copy(gear = g, uxRestricted = g == Gear.DRIVE || g == Gear.REVERSE) }
    fun setRearOccupied(v: Boolean) = _state.update { it.copy(rearSeatOccupied = v) }
    fun setCabinLight(v: Boolean) = _state.update { it.copy(cabinLightOn = v) }
    fun setUxRestricted(v: Boolean) = _state.update { it.copy(uxRestricted = v) }
    fun setBrake(v: Boolean) = _state.update { it.copy(brakePressed = v) }
    fun setPassengerWindowOpen(v: Boolean) = _state.update { it.copy(openWindows = if (v) listOf("조수석") else emptyList()) }

    override fun start() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (true) {
                delay(socIntervalMs)
                _state.update { s -> if (s.chargePortConnected == true) s.copy(soc = ((s.soc ?: 0) + 1).coerceAtMost(100)) else s }
            }
        }
    }

    override fun stop() { ticker?.cancel() }
    override fun refresh() = Unit

    override suspend fun readSnapshot(): CabinSnapshot {
        record("readSnapshot()")
        delay(200)
        return CabinSnapshot(backrestAngle = 20, seatVent = 0, ambientBrightness = 80, ambientColor = 0xFFFFFFFF.toInt(),
            cabinLightOn = false, hvacTemp = 24f, hvacFan = 3, hvacPower = true, hvacRecirculation = false)
    }

    override suspend fun applyRest(seatMode: SeatMode, halfRecline: Boolean) {
        set("Light.setAmbientLightBrightness(ROW_1_LEFT, ${PleosVehicleGateway.AMBIENT_DIM})")
        set("Light.setAmbientLightBrightnessColor(ROW_1_LEFT, warm)")
        set("Light.setCabinLightSwitch(OFF)")
        set("HVAC.setHvacTemperature(ROW_1_LEFT, ${PleosVehicleGateway.REST_TEMP_C}, CELSIUS)")
        set("HVAC.setHvacFanSpeed(ROW_1_LEFT, ${PleosVehicleGateway.REST_FAN})")
        set("HVAC.setHvacRecirculation(ROW_1_LEFT, false)")
        when (seatMode) {
            SeatMode.VENT -> set("Seat.setHvacSeatVentilation(ROW_1_LEFT, ${PleosVehicleGateway.SEAT_LEVEL})")
            SeatMode.NONE -> Unit
        }
        moveSeat("BACKWARD", if (halfRecline) 1_000 else 2_000)
    }

    override suspend fun restore(snapshot: CabinSnapshot) {
        restoreSeat(snapshot)
        set("Seat.setHvacSeatVentilation(ROW_1_LEFT, ${snapshot.seatVent})")
        set("Light.setAmbientLightBrightness(ROW_1_LEFT, ${snapshot.ambientBrightness})")
        set("Light.setCabinLightSwitch(${if (snapshot.cabinLightOn == true) "ON" else "OFF"})")
        set("HVAC.setHvacTemperature(ROW_1_LEFT, ${snapshot.hvacTemp}, CELSIUS)")
        set("HVAC.setHvacFanSpeed(ROW_1_LEFT, ${snapshot.hvacFan})")
    }

    override suspend fun restoreSeat(snapshot: CabinSnapshot) {
        val ms = reclinedMs
        if (ms <= 0) return
        moveSeat("FORWARD", ms)
    }

    override fun stopSeatMotion() {
        if (!_state.value.seatMoving) return
        seatJob?.cancel()
        record("Seat.moveSeatBackrestAngle(ROW_1_LEFT, STOP)")
        _state.update { it.copy(seatMoving = false) }
    }

    private suspend fun moveSeat(direction: String, ms: Long) {
        if (!set("Seat.moveSeatBackrestAngle(ROW_1_LEFT, $direction)")) return
        _state.update { it.copy(seatMoving = true) }
        val t0 = System.currentTimeMillis()
        try {
            val job = scope.launch { delay(ms) }
            seatJob = job
            job.join()
        } finally {
            withContext(NonCancellable) {
                val moved = System.currentTimeMillis() - t0
                reclinedMs = if (direction == "BACKWARD") reclinedMs + moved else (reclinedMs - moved).coerceAtLeast(0)
                if (_state.value.seatMoving) {
                    record("Seat.moveSeatBackrestAngle(ROW_1_LEFT, STOP)")
                    _state.update { it.copy(seatMoving = false) }
                }
            }
        }
    }

    /** 주행 제한 중엔 set을 보내지 않는다(Pleos 구현과 동일 게이트). */
    private fun set(line: String): Boolean {
        if (_state.value.drivingRestricted) return false
        record(line)
        return true
    }

    private fun record(line: String) = _log.update { (listOf(line) + it).take(30) }
}
