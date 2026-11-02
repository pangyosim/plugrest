package ai.pleos.plugrest.session

import ai.pleos.plugrest.vehicle.CabinSnapshot
import ai.pleos.plugrest.vehicle.Feature
import ai.pleos.plugrest.vehicle.Gear
import ai.pleos.plugrest.vehicle.SeatMode
import ai.pleos.plugrest.vehicle.VehicleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestSessionTest {
    private var clock = 0L
    private val session = RestSession(now = { clock })
    private val charging = VehicleState(connected = true, soc = 50, chargePortConnected = true, chargeSwitchOn = true, gear = Gear.PARK)

    @Test
    fun fullFlow_targetReached_countdown_restore() {
        assertNull(session.blockReason(charging))
        session.start(80, SeatMode.NONE, CabinSnapshot(), halfRecline = false)
        val resting = session.phase.value as Phase.Resting

        // 처음 3분은 계산 중
        session.onSoc(50)
        clock += 60_000; session.onSoc(51)
        assertNull(session.remainingMinutes(51, 80, resting.startedAt))

        // 1%/min → 남은 (80-55)=25분, 5분 단위
        clock += 60_000; session.onSoc(52)
        clock += 60_000; session.onSoc(53)
        clock += 60_000; session.onSoc(54)
        clock += 60_000; session.onSoc(55)
        assertEquals(25, session.remainingMinutes(55, 80, resting.startedAt))

        // 80% 초과 구간은 기울기 절반: 75→90 = 5 + 10/0.5 = 25
        assertEquals(25, session.remainingMinutes(75, 90, resting.startedAt))

        // 도달 → 10초 카운트다운
        session.evaluate(charging.copy(soc = 80))
        val restoring = session.phase.value as Phase.Restoring
        assertEquals(EndReason.TARGET_REACHED, restoring.reason)
        assertEquals(clock + 10_000, restoring.endsAt)

        // 브레이크 선제 복원은 1회만
        assertTrue(session.claimSeatRestore())
        assertFalse(session.claimSeatRestore())

        session.restoreCompleted()
        assertEquals(Phase.Done(80, restored = true, EndReason.TARGET_REACHED), session.phase.value)
    }

    @Test
    fun cancelDuringCountdown_leavesCabinAsIs() {
        session.start(80, SeatMode.VENT, CabinSnapshot(), halfRecline = false)
        session.requestStop()
        session.cancelRestore()
        assertEquals(Phase.Done(80, restored = false, EndReason.USER), session.phase.value)
    }

    @Test
    fun drivingRestriction_stopsWithoutRestore_thenOffersOnceWhenParked() {
        session.start(80, SeatMode.NONE, CabinSnapshot(backrestAngle = 10), halfRecline = false)
        // 기어 UNKNOWN(미지원)은 판정 보류
        session.evaluate(charging.copy(gear = Gear.UNKNOWN))
        assertTrue(session.phase.value is Phase.Resting)
        // 현실 순서: 케이블 분리 → 10초 예고 중 출발(D) → 복원 없이 Done
        session.evaluate(charging.copy(chargePortConnected = false))
        assertEquals(EndReason.CHARGING_STOPPED, (session.phase.value as Phase.Restoring).reason)
        session.evaluate(charging.copy(chargePortConnected = false, gear = Gear.DRIVE))
        assertEquals(Phase.Done(80, restored = false, EndReason.CHARGING_STOPPED), session.phase.value)
        // 포트가 빠진 채 재정차 → 세션을 닫지 않고 1회 제안 (C1)
        session.evaluate(charging.copy(chargePortConnected = false, gear = Gear.PARK))
        assertTrue((session.phase.value as Phase.Done).offerRestore)
        session.acceptOfferedRestore()
        assertTrue((session.phase.value as Phase.Restoring).silent)
        session.restoreCompleted()
        assertEquals(Phase.Done(80, restored = true, EndReason.CHARGING_STOPPED), session.phase.value)
        // 복원이 끝난 뒤에는 포트 분리로 세션 종료
        session.evaluate(charging.copy(chargePortConnected = false, gear = Gear.PARK))
        assertEquals(Phase.Idle, session.phase.value)
    }

    @Test
    fun unrestoredDone_expires2HoursAfterSessionStart() {
        session.start(80, SeatMode.NONE, CabinSnapshot(backrestAngle = 10), halfRecline = false)
        session.evaluate(charging.copy(gear = Gear.DRIVE))
        assertTrue(session.phase.value is Phase.Done)
        clock += 2 * 60 * 60_000L + 1_000
        session.evaluate(charging.copy(gear = Gear.DRIVE, chargePortConnected = false))
        assertEquals(Phase.Idle, session.phase.value)
    }

    @Test
    fun restrictionDuringCountdown_endsWithoutRestore() {
        session.start(80, SeatMode.NONE, CabinSnapshot(), halfRecline = false)
        session.evaluate(charging.copy(soc = 80))
        assertTrue(session.phase.value is Phase.Restoring)
        session.evaluate(charging.copy(soc = 80, uxRestricted = true))
        assertEquals(Phase.Done(80, restored = false, EndReason.TARGET_REACHED), session.phase.value)
    }

    @Test
    fun chargingDetection_portAndSocRise_orSwitch() {
        val noSwitch = charging.copy(chargeSwitchOn = null)
        assertFalse(session.isCharging(noSwitch))
        session.onSoc(50); clock += 30_000; session.onSoc(51)
        assertTrue(session.isCharging(noSwitch))
        assertFalse(session.isCharging(noSwitch.copy(chargePortConnected = false)))
        assertEquals("정차 후 이용할 수 있어요", session.blockReason(charging.copy(uxRestricted = true)))
    }

    @Test
    fun chargingHysteresis_holds90s_butPortDisconnectClearsImmediately() {
        val noSwitch = charging.copy(chargeSwitchOn = null)
        session.onSoc(50); clock += 30_000; session.onSoc(51)
        assertTrue(session.isCharging(noSwitch))            // t=30s: SOC 상승으로 판정
        clock += 150_000                                     // t=180s: 두 샘플 모두 3분 창 안 → 판정 시각 갱신
        assertTrue(session.isCharging(noSwitch))
        clock += 70_000                                      // t=250s: 창 밖이지만 마지막 판정 후 70s ≤ 90s 유지
        assertTrue(session.isCharging(noSwitch))
        assertFalse(session.isCharging(noSwitch.copy(chargePortConnected = false))) // 포트 분리는 즉시 해제
        session.onSoc(52); clock += 1_000; session.onSoc(53) // 다시 충전 중
        assertTrue(session.isCharging(noSwitch))
        clock += 300_000                                     // 창·유지 모두 지남
        assertFalse(session.isCharging(noSwitch))
    }

    @Test
    fun done_endsWhenPortDisconnected() {
        session.start(80, SeatMode.NONE, CabinSnapshot(), halfRecline = false)
        session.evaluate(charging.copy(soc = 80))
        session.restoreCompleted()
        assertTrue(session.phase.value is Phase.Done)
        session.evaluate(charging.copy(soc = 80))
        assertTrue(session.phase.value is Phase.Done)
        session.evaluate(charging.copy(soc = 80, chargePortConnected = false))
        assertEquals(Phase.Idle, session.phase.value)
    }

    @Test
    fun charging_portNullIsUnknown_unlessPortUnsupported() {
        val s = charging.copy(chargePortConnected = null)
        assertFalse(session.isCharging(s))                                                 // 포트 모름 → 충전 아님
        assertTrue(session.isCharging(s.copy(unsupported = setOf(Feature.CHARGE_PORT))))   // 포트 미지원 → 스위치만으로 판정
    }

    @Test
    fun resume_discardsSessionOlderThan2Hours() {
        val old = Phase.Resting(80, startedAt = 0L, SeatMode.NONE, halfRecline = false)
        clock = 3 * 60 * 60_000L
        assertFalse(session.resume(old, CabinSnapshot()))
        assertEquals(Phase.Idle, session.phase.value)
    }
}
