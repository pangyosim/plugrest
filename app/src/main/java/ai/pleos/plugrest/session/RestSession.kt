package ai.pleos.plugrest.session

import ai.pleos.plugrest.vehicle.CabinSnapshot
import ai.pleos.plugrest.vehicle.Feature
import ai.pleos.plugrest.vehicle.Gear
import ai.pleos.plugrest.vehicle.SeatMode
import ai.pleos.plugrest.vehicle.VehicleState
import ai.pleos.plugrest.vehicle.drivingRestricted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class EndReason { TARGET_REACHED, CHARGING_STOPPED, USER, RESTRICTED }

sealed interface Phase {
    data object Idle : Phase
    data class Resting(val target: Int, val startedAt: Long, val seatMode: SeatMode, val halfRecline: Boolean) : Phase
    /** silent: 사용자가 "되돌리기"를 직접 눌러 즉시 복원하는 경우(예고 TTS·카운트다운 없음) */
    data class Restoring(val target: Int, val endsAt: Long, val reason: EndReason, val silent: Boolean = false) : Phase
    /** restored=false 이면 시트·조명·공조는 지금 상태 그대로. offerRestore: 주행 후 다시 정차했을 때 1회 "되돌릴까요?" 제안. */
    data class Done(val target: Int, val restored: Boolean, val reason: EndReason, val offerRestore: Boolean = false) : Phase
}

/**
 * 순수 Kotlin 상태머신. 차량·TTS·저장소를 모르고, 시간은 [now]로만 본다.
 * Idle → Resting → Restoring(10초 카운트다운) → Done → Idle
 */
class RestSession(
    private val now: () -> Long = System::currentTimeMillis,
    /** 데모 모드: 3분 → 20초 */
    private val calculatingMs: Long = 3 * 60_000L,
    /** 최근 SOC 상승을 보는 창. 데모 모드: 3분 → 20초 */
    private val chargingWindowMs: Long = 3 * 60_000L,
    private val restoreDelayMs: Long = 10_000L,
    /** 히스테리시스: 한 번 충전 중으로 판정되면 SOC 상승이 없어도 이 시간 동안 유지(버튼 깜빡임 방지). 포트 분리는 즉시 해제 */
    private val chargingHoldMs: Long = 90_000L,
    /** 충전 속도 학습 (PRD §4.6). 세션 종료 시 유효 기울기를 기록 */
    val rates: ChargeRateMemory = ChargeRateMemory(),
) {
    private val _phase = MutableStateFlow<Phase>(Phase.Idle)
    val phase: StateFlow<Phase> = _phase
    var snapshot: CabinSnapshot? = null
        private set
    private val history = ArrayDeque<Pair<Long, Int>>() // (ms, soc)
    private var seatRestored = false
    private var wasRestricted = false
    private var lastChargingAt = Long.MIN_VALUE
    private var sessionStartedAt = 0L

    @Synchronized fun onSoc(soc: Int) {
        val t = now()
        history.addLast(t to soc)
        while (history.size > 1 && t - history.first().first > HISTORY_MS) history.removeFirst()
    }

    @Synchronized fun socRisingWithin(ms: Long): Boolean {
        val t = now()
        val recent = history.filter { t - it.first <= ms }
        return recent.size >= 2 && recent.last().second > recent.first().second
    }

    /**
     * 충전 중 판정 (PM 결정 11-03):
     * - 포트 지원: 포트 == true AND (최근 3분 SOC 상승 OR 충전 스위치 ON). 포트 null은 '모름' → 충전 아님.
     * - 포트 미지원(capability 없음): (최근 3분 SOC 상승 OR 스위치 ON)
     * - 한 번 판정되면 90초 유지(히스테리시스), 포트 분리는 즉시 해제.
     */
    @Synchronized fun isCharging(s: VehicleState): Boolean {
        if (s.chargePortConnected == false) { lastChargingAt = Long.MIN_VALUE; return false }
        val t = now()
        val signal = socRisingWithin(chargingWindowMs) || s.chargeSwitchOn == true
        val portOk = if (Feature.CHARGE_PORT in s.unsupported) true else s.chargePortConnected == true
        if (portOk && signal) { lastChargingAt = t; return true }
        return lastChargingAt != Long.MIN_VALUE && t - lastChargingAt <= chargingHoldMs
    }

    fun isDrivingRestricted(s: VehicleState): Boolean = s.drivingRestricted

    /** null이면 시작 가능. 아니면 버튼 아래 한 줄 이유. */
    @Synchronized fun blockReason(s: VehicleState): String? = when {
        _phase.value !is Phase.Idle -> "진행 중"
        !s.connected -> "차량 연결 중…"
        isDrivingRestricted(s) -> "정차 후 이용할 수 있어요"
        !isCharging(s) -> "충전을 시작하면 쓸 수 있어요"
        else -> null
    }

    @Synchronized fun start(target: Int, seatMode: SeatMode, snapshot: CabinSnapshot, halfRecline: Boolean) {
        this.snapshot = snapshot
        seatRestored = false
        sessionStartedAt = now()
        _phase.value = Phase.Resting(target, sessionStartedAt, seatMode, halfRecline)
    }

    /** 프로세스 재생성 후 복구. 2시간 지났으면 폐기(PRD v0.5). */
    @Synchronized fun resume(resting: Phase.Resting, snapshot: CabinSnapshot): Boolean {
        if (now() - resting.startedAt > MAX_SESSION_MS) return false
        this.snapshot = snapshot
        sessionStartedAt = resting.startedAt
        _phase.value = resting
        return true
    }

    /**
     * 남은 분. null = "계산 중"(시작 후 [calculatingMs] 이내). 0 = 도달.
     * 최근 5분 기울기, 80% 초과 구간은 기울기×0.5, 5분 단위 반올림(최소 5).
     */
    @Synchronized fun remainingMinutes(soc: Int, target: Int, startedAt: Long): Int? {
        if (soc >= target) return 0
        if (now() - startedAt < calculatingMs) return null
        val slope = slopePctPerMin() ?: FALLBACK_PCT_PER_MIN
        return etaMinutes(soc, target, slope, slope * ABOVE_80_FACTOR)
    }

    /** Resting 종료 시 1회: 실측 기울기(최소 2분 데이터)가 있으면 학습 */
    private fun learn() { slopePctPerMin()?.let { rates.record(it, history.last().second) } }

    private fun slopePctPerMin(): Double? {
        val t = now()
        val window = history.filter { t - it.first <= SLOPE_WINDOW_MS }
        if (window.size < 2) return null
        val dtMin = (window.last().first - window.first().first) / 60_000.0
        if (dtMin < MIN_SLOPE_SPAN_MIN) return null // 데이터 구간이 짧으면 기본 가정 사용(슬라이더 점프·초기 노이즈 방지)
        val slope = (window.last().second - window.first().second) / dtMin
        return if (slope > 0) slope else null
    }

    /** 차량 상태가 바뀔 때마다 호출(서비스 단일 경로). 전이가 있으면 phase가 바뀐다. */
    @Synchronized fun evaluate(s: VehicleState) {
        val restricted = isDrivingRestricted(s)
        when (val p = _phase.value) {
            is Phase.Resting -> when {
                restricted -> { learn(); finish(Phase.Done(p.target, restored = false, EndReason.RESTRICTED)) }
                now() - p.startedAt > MAX_SESSION_MS -> beginRestore(p.target, EndReason.USER) // 2시간 만료
                s.soc != null && s.soc >= p.target -> beginRestore(p.target, EndReason.TARGET_REACHED)
                s.chargePortConnected == false -> beginRestore(p.target, EndReason.CHARGING_STOPPED)
            }
            is Phase.Restoring -> if (restricted) finish(Phase.Done(p.target, restored = false, p.reason))
            is Phase.Done -> when {
                // 포트 분리로 종료하는 건 복원이 끝났거나 되돌릴 게 없을 때만. 미복원 Done은 재정차 제안을 위해 유지 (검수 C1, PM 승인)
                s.chargePortConnected == false && (p.restored || snapshot == null) -> reset()
                now() - sessionStartedAt > MAX_SESSION_MS -> reset() // 세션 시작 시각 기준 2시간 → 폐기
                // 미복원 상태로 주행했다가 다시 정차 → 1회 제안 (PRD v0.5 §4.4)
                !p.restored && !p.offerRestore && wasRestricted && !restricted && snapshot != null ->
                    _phase.value = p.copy(offerRestore = true)
            }
            Phase.Idle -> Unit
        }
        wasRestricted = restricted
    }

    @Synchronized fun requestStop() {
        val p = _phase.value as? Phase.Resting ?: return
        beginRestore(p.target, EndReason.USER)
    }

    private fun beginRestore(target: Int, reason: EndReason) {
        learn()
        _phase.value = Phase.Restoring(target, now() + restoreDelayMs, reason)
    }

    private fun finish(done: Phase.Done) { _phase.value = done }

    @Synchronized fun cancelRestore() {
        val p = _phase.value as? Phase.Restoring ?: return
        snapshot = null // 사용자가 "그대로"를 택했으므로 이후 재정차 제안 없음 (검수 D1)
        finish(Phase.Done(p.target, restored = false, p.reason))
    }

    /** Done 유지 만료까지 남은 ms (서비스가 타이머로 evaluate를 1회 더 부르기 위함, 검수 D2) */
    @Synchronized fun sessionRemainingMs(): Long = (sessionStartedAt + MAX_SESSION_MS - now()).coerceAtLeast(0)

    @Synchronized fun restoreCompleted() {
        val p = _phase.value as? Phase.Restoring ?: return
        finish(Phase.Done(p.target, restored = true, p.reason))
    }

    /** 재정차 제안에서 "되돌리기" → 예고 없이 즉시 복원 */
    @Synchronized fun acceptOfferedRestore() {
        val p = _phase.value as? Phase.Done ?: return
        if (!p.offerRestore) return
        _phase.value = Phase.Restoring(p.target, now(), p.reason, silent = true)
    }

    @Synchronized fun declineOfferedRestore() {
        val p = _phase.value as? Phase.Done ?: return
        _phase.value = p.copy(offerRestore = false)
    }

    /** 브레이크 페달 → 시트 선제 복원은 세션당 1회. true면 호출자가 복원을 실행. */
    @Synchronized fun claimSeatRestore(): Boolean {
        val active = _phase.value is Phase.Resting || _phase.value is Phase.Restoring
        if (!active || seatRestored) return false
        seatRestored = true
        return true
    }

    @Synchronized fun reset() {
        _phase.value = Phase.Idle
        snapshot = null
        history.clear()
        seatRestored = false
        lastChargingAt = Long.MIN_VALUE
    }

    companion object {
        const val HISTORY_MS = 10 * 60_000L
        const val SLOPE_WINDOW_MS = 5 * 60_000L
        const val MIN_SLOPE_SPAN_MIN = 2.0
        const val MAX_SESSION_MS = 2 * 60 * 60_000L
        // ponytail: 급속 50kW / 아이오닉5 롱레인지 77.4kWh 가정 = 1.08 %/min. 실차 데이터로 교정하는 보정 상수.
        const val FALLBACK_PCT_PER_MIN = 50.0 / 77.4 * 100.0 / 60.0
        // ponytail: 80% 초과 구간 감속 계수(추정). 실데이터로 교정.
        const val ABOVE_80_FACTOR = 0.5
    }
}
