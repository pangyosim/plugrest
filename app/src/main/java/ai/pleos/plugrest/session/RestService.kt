package ai.pleos.plugrest.session

import ai.pleos.plugrest.App
import ai.pleos.plugrest.R
import ai.pleos.plugrest.vehicle.SeatMode
import ai.pleos.plugrest.vehicle.drivingRestricted
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 휴식 세션 호스트(Foreground Service). 리스너 평가·TTS·복원을 소유한다. Activity는 뷰만.
 * 흐름: START → 스냅샷 → applyRest → Resting … evaluate → Restoring(10초) → restore → Done → 알림 종료
 */
class RestService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e ->
        Log.e("RestService", "session failed", e)
        try { g.gateway.stopSeatMotion(); g.session.reset(); g.store.clear() } finally { stopSelf() }
    })
    private val g get() = App.graph
    private var countdown: Job? = null
    private var wakeRepeat: Job? = null
    private var expiry: Job? = null
    /** 차량 제어(applyRest/복원/선제 복원)는 항상 이 Job 하나. 주행 제한 진입 시 통째로 취소. */
    private var control: Job? = null
    private var sessionSeen = false // 시작 직후의 Idle(초기값)로 자기 종료하지 않기 위함

    private fun control(block: suspend () -> Unit) {
        control?.cancel()
        control = scope.launch { block() }
    }

    private fun abortControl() {
        control?.cancel(); countdown?.cancel()
        g.gateway.stopSeatMotion()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground()
        // 차량 상태 → 세션 평가
        scope.launch {
            g.gateway.state.collect { s ->
                s.soc?.let { g.session.onSoc(it) }
                if (s.drivingRestricted) abortControl() // 복원 없이 모든 제어 중단 (PRD v0.3)
                g.session.evaluate(s)
                if (s.brakePressed && !s.drivingRestricted && g.session.claimSeatRestore()) {
                    g.session.snapshot?.let { snap -> control { g.gateway.restoreSeat(snap) } }
                }
            }
        }
        // 세션 전이 → TTS·카운트다운·복원·저장
        scope.launch {
            g.session.phase.collect { p ->
                if (p !is Phase.Idle) sessionSeen = true
                when (p) {
                    is Phase.Resting -> g.session.snapshot?.let { g.store.save(p, it) }
                    is Phase.Restoring -> {
                        if (!p.silent) g.narrator.speak(when (p.reason) {
                            EndReason.TARGET_REACHED -> "목표 ${p.target}%에 도달했습니다. 10초 후 시트를 원래대로 되돌립니다."
                            EndReason.CHARGING_STOPPED -> "충전이 중단되었습니다. 10초 후 시트를 원래대로 되돌립니다."
                            else -> "휴식 모드를 종료합니다. 10초 후 시트를 원래대로 되돌립니다."
                        })
                        countdown?.cancel()
                        countdown = launch {
                            delay((p.endsAt - System.currentTimeMillis()).coerceAtLeast(0))
                            if (g.session.phase.value == p) control {
                                g.session.snapshot?.let { g.gateway.restore(it) }
                                g.session.restoreCompleted()
                            }
                        }
                    }
                    is Phase.Done -> {
                        countdown?.cancel()
                        g.store.clear()
                        updateNotification("휴식 모드 종료 · 목표 ${p.target}%")
                        if (p.offerRestore) g.narrator.speak("시트를 원래대로 되돌릴까요?")
                        // 차량 상태 변화가 없어도 2시간 만료가 적용되도록 1회 타이머 (검수 D2)
                        if (!p.restored && expiry == null) expiry = launch { delay(g.session.sessionRemainingMs() + 1_000); g.session.evaluate(g.gateway.state.value) }
                        // 사용자 반응 없으면 30초 간격 최대 3회 다시 깨움 (PRD v0.5) — 복원 완료·정차 상태에서만
                        if (p.reason == EndReason.TARGET_REACHED && p.restored && wakeRepeat == null) wakeRepeat = launch {
                            repeat(3) {
                                delay(30_000)
                                if (g.session.phase.value is Phase.Done && !g.gateway.state.value.drivingRestricted) g.narrator.speak("목표 ${p.target}%에 도달했습니다.")
                            }
                            if (g.session.phase.value is Phase.Done) g.session.reset() // 재안내 3회 끝 → 세션 종료
                        }
                    }
                    Phase.Idle -> if (sessionSeen) { abortControl(); wakeRepeat?.cancel(); wakeRepeat = null; expiry?.cancel(); expiry = null; stopSelf() }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val target = intent?.getIntExtra(EXTRA_TARGET, 0) ?: 0
        if (target > 0 && g.session.phase.value is Phase.Idle) {
            val seatMode = SeatMode.valueOf(intent?.getStringExtra(EXTRA_SEAT) ?: SeatMode.NONE.name)
            control {
                // 뒷좌석 착좌 → 절반만. 못 읽으면(null) 보수적으로 절반 (PRD v0.5)
                val halfRecline = g.gateway.state.value.rearSeatOccupied != false
                val snapshot = g.gateway.readSnapshot()
                g.session.start(target, seatMode, snapshot, halfRecline)
                updateNotification("휴식 모드 진행 중 · 목표 $target%")
                g.narrator.speak("휴식 모드를 시작합니다. 목표 $target%에 도달하면 깨워 드릴게요.")
                g.gateway.applyRest(seatMode, halfRecline)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        if (!g.activityVisible) g.gateway.stop() // 리스너 unregister + Vehicle.release (화면이 떠 있으면 Activity.onStop이 담당)
        super.onDestroy()
    }

    private fun startForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "휴식 모드", NotificationManager.IMPORTANCE_LOW))
        val n = buildNotification("휴식 모드 진행 중")
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIF_ID, n)
    }

    private fun updateNotification(text: String) =
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))

    private fun buildNotification(text: String): Notification =
        Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .build()

    companion object {
        private const val CHANNEL = "rest"
        private const val NOTIF_ID = 1
        private const val EXTRA_TARGET = "target"
        private const val EXTRA_SEAT = "seat"

        fun start(context: Context, target: Int = 0, seatMode: SeatMode = SeatMode.NONE) {
            val i = Intent(context, RestService::class.java).putExtra(EXTRA_TARGET, target).putExtra(EXTRA_SEAT, seatMode.name)
            ContextCompat.startForegroundService(context, i)
        }
    }
}
