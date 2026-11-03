package ai.pleos.plugrest.vehicle

import kotlinx.coroutines.flow.StateFlow

enum class Gear { PARK, DRIVE, REVERSE, NEUTRAL, UNKNOWN }
enum class SeatMode { NONE, VENT } // 열선은 PRD v0.5에서 제외(통풍 끔/켬만)

/** 차량이 지원하지 않아 건너뛴 기능. UI에 "· 이 차량 미지원"으로만 표시. */
enum class Feature(val label: String) {
    SEAT_RECLINE("등받이"), SEAT_VENT("통풍 시트"),
    AMBIENT("앰비언트 라이트"), CABIN_LIGHT("실내등"), HVAC("공조"),
    GEAR("기어"), CHARGE_PORT("충전 포트"), BRAKE("브레이크"), WINDOW("창문"), OCCUPANCY("착좌 감지"),
}

data class VehicleState(
    val connected: Boolean = false,
    val soc: Int? = null,
    val chargePortConnected: Boolean? = null, // null = 미확인/미지원
    val chargeSwitchOn: Boolean? = null,      // 보조 신호 (Idle에서만 폴링)
    val gear: Gear? = null,                   // null = 미확인/미지원 (보조 신호)
    val brakePressed: Boolean = false,
    val uxRestricted: Boolean = false,        // CarUxRestrictionsManager.isRequiresDistractionOptimization
    val openWindows: List<String> = emptyList(),
    val cabinLightOn: Boolean = false,
    val rearSeatOccupied: Boolean? = null,      // null = 못 읽음 → 보수적으로 절반 리클라인
    val unsupported: Set<Feature> = emptySet(),
    val seatMoving: Boolean = false,
)

/**
 * 주행 제한: CarUxRestrictions가 1순위. 기어는 D/R/N이면 제한, null/UNKNOWN(기어 API 미지원)은 판정 보류.
 */
val VehicleState.drivingRestricted: Boolean
    get() = uxRestricted || (gear != null && gear != Gear.PARK && gear != Gear.UNKNOWN)

/** capability로 받은 범위가 있으면 그 안으로 자른다. 범위를 모르면 그대로. */
fun clampTo(value: Int, range: IntRange?): Int = range?.takeIf { !it.isEmpty() }?.let { value.coerceIn(it) } ?: value // 빈 범위(min>max)는 무시

/** 앰비언트 밝기 비율(0~100%) → capability 범위의 실제 값. 범위를 모르면 0~100 가정 (검수 I2). */
fun ambientFromPct(pct: Int, range: IntRange?): Int = range?.takeIf { !it.isEmpty() }?.let { it.first + (it.last - it.first) * pct / 100 } ?: pct

/** 휴식 모드 시작 전 차량 상태. 종료 시 그대로 되돌린다. null = 읽지 못함(복원도 건너뜀). */
data class CabinSnapshot(
    val backrestAngle: Int? = null,
    val seatVent: Int? = null,
    val ambientBrightness: Int? = null,
    val ambientColor: Int? = null,
    val cabinLightOn: Boolean? = null,
    val hvacTemp: Float? = null,
    val hvacFan: Int? = null,
    val hvacPower: Boolean? = null,
    val hvacRecirculation: Boolean? = null,
)

/** 앱이 차량을 보는 유일한 창구. 구현: PleosVehicleGateway(SDK), FakeVehicleGateway(일반 AVD·테스트). */
interface VehicleGateway {
    val state: StateFlow<VehicleState>
    fun start()
    fun stop()
    /** Idle 화면에서 10초마다 호출: 충전 스위치 등 리스너가 없는 값을 다시 읽는다. */
    fun refresh()
    suspend fun readSnapshot(): CabinSnapshot
    suspend fun applyRest(seatMode: SeatMode, halfRecline: Boolean)
    suspend fun restore(snapshot: CabinSnapshot)
    suspend fun restoreSeat(snapshot: CabinSnapshot)
    /** 시트 이동 중 화면 터치 → 즉시 정지. */
    fun stopSeatMotion()
    /** 선라이즈 기상용 단일 set (PRD §4.8). 미지원·주행 제한이면 무시. */
    suspend fun setAmbientBrightness(value: Int)
    suspend fun setHvacFan(level: Int)
    /** 밝기 비율 → 이 차량의 실제 값 (capability 범위 기준) */
    fun ambientLevel(pct: Int): Int = pct
}
