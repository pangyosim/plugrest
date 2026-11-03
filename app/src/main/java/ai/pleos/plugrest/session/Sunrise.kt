package ai.pleos.plugrest.session

import ai.pleos.plugrest.vehicle.VehicleGateway
import kotlinx.coroutines.delay

/** 선라이즈 기상 (PRD §4.8): 앰비언트 밝기를 from→to로 durationMs 동안 선형 상승(10초 간격), 풍량 1→2. 순수 Kotlin. */
object Sunrise {
    const val STEP_MS = 30_000L      // 실차 30초 간격 (R4). 데모: 10초
    const val DEFAULT_TO_PCT = 70   // 스냅샷에 복원 전 밝기가 없을 때: 범위 최소 + 70%
    const val FAN = 2

    /** 단계별 목표 밝기(마지막 = to, 단계 수 = durationMs/stepMs). to ≤ from(스냅샷 밝기 0/OFF 등)이면 빈 목록 → 조명 램프 생략 (검수 R4). */
    fun schedule(from: Int, to: Int, durationMs: Long, stepMs: Long = STEP_MS): List<Int> {
        val n = (durationMs / stepMs).toInt()
        if (to <= from || n <= 0) return emptyList()
        return (1..n).map { from + (to - from) * it / n }
    }

    /**
     * 풍량 1→2(충전 중일 때만, R5. ponytail: 절전 하한은 SDK에 신호가 없어 미반영) 먼저, 이후 stepMs마다 밝기 set(값이 바뀔 때만).
     * 취소되면 그 자리에서 멈춘다 — 복원은 기존 경로. 모든 set은 게이트웨이의 주행 제한·미지원 게이트를 거친다.
     */
    suspend fun run(gateway: VehicleGateway, from: Int, to: Int, durationMs: Long, stepMs: Long = STEP_MS, charging: Boolean = true) {
        if (charging) gateway.setHvacFan(FAN)
        var last = from
        for (b in schedule(from, to, durationMs, stepMs)) {
            if (b != last) { gateway.setAmbientBrightness(b); last = b }
            delay(stepMs)
        }
    }
}
