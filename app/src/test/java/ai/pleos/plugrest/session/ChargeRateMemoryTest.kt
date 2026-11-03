package ai.pleos.plugrest.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChargeRateMemoryTest {
    @Test
    fun ema_updates_and_persists_through_onChange() {
        var saved: ChargeRateMemory.Rates? = null
        val m = ChargeRateMemory(onChange = { saved = it })
        assertNull(m.estimateMinutes(50, 80))

        m.record(1.0, 60)                 // 첫 관측은 그대로
        assertEquals(1.0, m.rates.value.below80.ema, 1e-9)
        m.record(2.0, 60)                 // EMA α=0.3 → 0.3·2 + 0.7·1 = 1.3
        assertEquals(1.3, m.rates.value.below80.ema, 1e-9)
        assertEquals(2, m.rates.value.count)
        assertEquals(m.rates.value, saved)

        // 50→80 @1.3%/min = 23.1분 → 5분 반올림 25
        assertEquals(25, m.estimateMinutes(50, 80))
        m.record(0.0, 60)                 // 무효 기울기는 무시
        m.record(12.0, 60)                // 비현실 기울기(>5%/분, 데모 슬라이더)도 무시 (F3)
        assertEquals(2, m.rates.value.count)

        m.clear()
        assertNull(m.estimateMinutes(50, 80))
        assertEquals(ChargeRateMemory.Rates(), saved)
    }

    @Test
    fun buckets_split_at_80_with_taper_fallback() {
        val m = ChargeRateMemory()
        m.record(1.0, 70)                 // ≤80 버킷만
        // 75→90: 5/1.0 + 10/(1.0×0.5) = 25
        assertEquals(25, m.estimateMinutes(75, 90))

        m.record(0.8, 85)                 // >80 버킷 실측 → 테이퍼 대신 실측 사용: 5 + 10/0.8 = 17.5 → 20
        assertEquals(20, m.estimateMinutes(75, 90))
        assertEquals(1, m.rates.value.above80.n)
        assertEquals(0, m.estimateMinutes(90, 90))
    }
}
