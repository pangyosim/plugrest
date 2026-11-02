package ai.pleos.plugrest.vehicle

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** B2 회귀: applyRest 중 주행 제한(기어 D) 진입 → 제어 Job 취소 → STOP이 찍히고 그 뒤 set은 0회. */
class FakeVehicleGatewayTest {
    @Test
    fun cancelDuringRecline_sendsStop_andNoFurtherSets() = runBlocking {
        val fake = FakeVehicleGateway()
        val job = launch { fake.applyRest(SeatMode.VENT, halfRecline = false) }
        delay(300) // 조명·공조 set 후 등받이 BACKWARD 진행 중
        assertTrue(fake.state.value.seatMoving)
        fake.setGear(Gear.DRIVE)          // 주행 제한 진입
        job.cancel(); job.join()          // 서비스의 abortControl()과 동일
        fake.stopSeatMotion()
        val log = fake.log.value
        assertEquals("Seat.moveSeatBackrestAngle(ROW_1_LEFT, STOP)", log.first())
        assertFalse(fake.state.value.seatMoving)
        val before = log.size
        fake.restore(CabinSnapshot(hvacTemp = 24f, hvacFan = 3)) // 제한 중 복원 시도 → 전부 게이트
        assertEquals(before, fake.log.value.size)
    }

    @Test
    fun restoreSeat_isIdempotent_andOnlyAfterRecline() = runBlocking {
        val fake = FakeVehicleGateway()
        fake.restoreSeat(CabinSnapshot())
        assertTrue(fake.log.value.none { "FORWARD" in it }) // 리클라인 안 했으면 복원 명령 없음
        fake.applyRest(SeatMode.NONE, halfRecline = true)
        fake.restoreSeat(CabinSnapshot())
        fake.restoreSeat(CabinSnapshot())
        assertEquals(1, fake.log.value.count { "FORWARD" in it })
    }

    @Test
    fun restoreSeat_cancelledMidway_keepsRemainingDistance() = runBlocking {
        val fake = FakeVehicleGateway()
        fake.applyRest(SeatMode.NONE, halfRecline = false)          // BACKWARD 2000ms
        val job = launch { fake.restoreSeat(CabinSnapshot()) }
        delay(500); job.cancel(); job.join()                         // 복원 도중 취소 (제한 진입·새 control 작업)
        assertFalse(fake.state.value.seatMoving)
        fake.restoreSeat(CabinSnapshot())                            // 남은 ~1500ms만큼 다시 FORWARD
        assertEquals(2, fake.log.value.count { "FORWARD" in it })
        fake.restoreSeat(CabinSnapshot())                            // 다 되돌렸으면 더 이상 명령 없음
        assertEquals(2, fake.log.value.count { "FORWARD" in it })
    }
}
