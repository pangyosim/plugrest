package ai.pleos.plugrest.session

import ai.pleos.plugrest.vehicle.FakeVehicleGateway
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SunriseTest {
    @Test
    fun schedule_linear_stepCount_andSkipWhenNotBrighter() {
        // 데모: 30초 / 10초 간격 = 3단계, 마지막 = 복원 전 밝기
        assertEquals(listOf(36, 53, 70), Sunrise.schedule(20, 70, 30_000, 10_000))
        // 실차: 3분 / 30초 = 6단계
        assertEquals(6, Sunrise.schedule(20, 80, 180_000, 30_000).size)
        // 스냅샷 밝기가 0/OFF(≤ 현재)면 조명 램프 생략 (R4)
        assertTrue(Sunrise.schedule(20, 0, 180_000).isEmpty())
    }

    @Test
    fun run_cancelled_stopsSetting_andNoFanWhenNotCharging() = runBlocking {
        val fake = FakeVehicleGateway()
        val job = launch { Sunrise.run(fake, 20, 70, durationMs = 500, stepMs = 50) }
        delay(125) // 풍량 1회 + 밝기 3회쯤
        job.cancel(); job.join()
        val sets = fake.log.value.filter { "setAmbientLightBrightness" in it }
        assertTrue(sets.size in 2..4)
        assertEquals(listOf("HVAC.setHvacFanSpeed(ROW_1_LEFT, ${Sunrise.FAN})"), fake.log.value.filter { "setHvacFanSpeed" in it })
        delay(200)
        assertEquals(sets.size, fake.log.value.count { "setAmbientLightBrightness" in it }) // 취소 후 추가 set 없음
        assertTrue(fake.ambient.value in 21..70)

        val fake2 = FakeVehicleGateway()
        Sunrise.run(fake2, 20, 70, durationMs = 100, stepMs = 50, charging = false) // 충전 중 아니면 풍량 상승 없음 (R5)
        assertTrue(fake2.log.value.none { "setHvacFanSpeed" in it })
        assertEquals(70, fake2.ambient.value)
    }
}
