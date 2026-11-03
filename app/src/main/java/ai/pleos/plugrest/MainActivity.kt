package ai.pleos.plugrest

import ai.pleos.plugrest.session.Phase
import ai.pleos.plugrest.ui.DoneScreen
import ai.pleos.plugrest.ui.FakePanel
import ai.pleos.plugrest.ui.IdleScreen
import ai.pleos.plugrest.ui.RestingScreen
import ai.pleos.plugrest.ui.RestoringScreen
import ai.pleos.plugrest.ui.tick
import ai.pleos.plugrest.vehicle.FakeVehicleGateway
import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val g get() = App.graph

    override fun onStart() { super.onStart(); g.activityVisible = true; g.gateway.start() } // 멱등
    override fun onStop() {
        super.onStop()
        g.activityVisible = false
        if (g.session.phase.value is Phase.Idle) g.gateway.stop() // 세션 중엔 서비스가 리스너를 써야 하므로 유지
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val phase by g.session.phase.collectAsState()
            val s by g.gateway.state.collectAsState()
            val now = tick()

            // Idle에서만 10초 폴링 (충전 스위치 등 리스너 없는 값)
            LaunchedEffect(phase is Phase.Idle) { while (phase is Phase.Idle) { g.gateway.refresh(); delay(10_000) } }
            // Resting: 화면 밝기 최소
            LaunchedEffect(phase is Phase.Resting || phase is Phase.Restoring) {
                window.attributes = window.attributes.apply {
                    screenBrightness = if (phase is Phase.Resting || phase is Phase.Restoring) 0.1f else -1f
                }
            }

            Column(Modifier.fillMaxSize().navigationBarsPadding()) { // 시스템 제스처 바와 Fake 패널 핸들이 겹치지 않게
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                    when (val p = phase) {
                        Phase.Idle -> IdleScreen(s, g.session.blockReason(s), g.session.rates) { target, seat -> g.startRest(this@MainActivity, target, seat) }
                        is Phase.Resting -> RestingScreen(p, s, g.session.remainingMinutes(s.soc ?: 0, p.target, p.startedAt).also { now },
                            learned = g.session.rates.estimateMinutes(s.soc ?: 0, p.target), measured = g.session.hasMeasuredSlope(),
                            onStop = { g.session.requestStop() }, onTouch = { g.gateway.stopSeatMotion() })
                        is Phase.Restoring -> RestoringScreen(p, s, now, onCancel = { g.gateway.stopSeatMotion(); g.session.cancelRestore() }, onTouch = { g.gateway.stopSeatMotion() })
                        is Phase.Done -> DoneScreen(p, s,
                            onConfirm = { g.session.reset() },
                            onRestore = { g.session.acceptOfferedRestore() }, // 복원 자체는 서비스의 control Job이 수행
                            onKeep = { g.session.declineOfferedRestore() })
                    }
                }
                (g.gateway as? FakeVehicleGateway)?.let { FakePanel(it) }
            }
        }
    }
}
