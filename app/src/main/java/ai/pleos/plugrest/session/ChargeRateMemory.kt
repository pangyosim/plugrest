package ai.pleos.plugrest.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

/**
 * 충전 속도 학습 (PRD §4.6). 기기 로컬, VIN·계정 없음("이 차 = 이 기기").
 * 세션 종료 시 유효 기울기(%/분)를 SOC ≤80 / >80 두 버킷에 EMA(α=0.3)+횟수 n으로 누적.
 * [onChange]로 저장소에 그대로 흘려보낸다(순수 Kotlin, Android 모름).
 */
class ChargeRateMemory(initial: Rates = Rates(), private val onChange: (Rates) -> Unit = {}) {
    data class Bucket(val ema: Double = 0.0, val n: Int = 0)
    data class Rates(val below80: Bucket = Bucket(), val above80: Bucket = Bucket()) {
        val count get() = below80.n + above80.n
    }

    private val _rates = MutableStateFlow(initial)
    val rates: StateFlow<Rates> = _rates

    /** ponytail: 버킷은 기울기 창 끝 SOC로 고름(창이 80%를 걸치면 섞임). 실데이터로 문제 되면 창을 80에서 쪼갠다. */
    fun record(pctPerMin: Double, soc: Int) {
        if (pctPerMin <= 0 || pctPerMin > MAX_PCT_PER_MIN) return // 비현실 기울기(데모 슬라이더 등)는 학습하지 않음 (검수 F3)
        _rates.update { r -> if (soc <= 80) r.copy(below80 = r.below80 + pctPerMin) else r.copy(above80 = r.above80 + pctPerMin) }
        onChange(_rates.value)
    }

    private operator fun Bucket.plus(v: Double) = Bucket(if (n == 0) v else ALPHA * v + (1 - ALPHA) * ema, n + 1)

    /** 지난 충전 기준 예상 분. 기록이 전혀 없으면 null. 한쪽 버킷만 있으면 ×0.5 테이퍼로 다른 쪽을 추정. */
    fun estimateMinutes(soc: Int, target: Int): Int? {
        val r = _rates.value
        val below = r.below80.takeIf { it.n > 0 }?.ema
            ?: r.above80.takeIf { it.n > 0 }?.ema?.div(RestSession.ABOVE_80_FACTOR)
            ?: return null
        val above = r.above80.takeIf { it.n > 0 }?.ema ?: below * RestSession.ABOVE_80_FACTOR
        return etaMinutes(soc, target, below, above)
    }

    fun clear() { _rates.value = Rates(); onChange(_rates.value) }

    companion object {
        const val ALPHA = 0.3
        /** ≈350kW / 77kWh. 이보다 빠르면 실측이 아니라 입력 오류로 본다 */
        const val MAX_PCT_PER_MIN = 5.0
    }
}

/** 남은 분: 80% 이하/초과 구간 기울기 분리, 5분 단위 반올림(최소 5). soc ≥ target → 0 */
fun etaMinutes(soc: Int, target: Int, slopeBelow80: Double, slopeAbove80: Double): Int {
    if (soc >= target) return 0
    val below80 = (minOf(target, 80) - soc).coerceAtLeast(0)
    val above80 = (target - maxOf(soc, 80)).coerceAtLeast(0)
    val minutes = below80 / slopeBelow80 + above80 / slopeAbove80
    return ((minutes / 5.0).roundToInt() * 5).coerceAtLeast(5)
}
