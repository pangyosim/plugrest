package ai.pleos.plugrest

import ai.pleos.plugrest.session.ChargeRateMemory
import ai.pleos.plugrest.session.RestService
import ai.pleos.plugrest.session.RestSession
import ai.pleos.plugrest.session.SessionStore
import ai.pleos.plugrest.session.Sunrise
import ai.pleos.plugrest.vehicle.AndroidNarrator
import ai.pleos.plugrest.vehicle.FakeVehicleGateway
import ai.pleos.plugrest.vehicle.Narrator
import ai.pleos.plugrest.vehicle.PleosNarrator
import ai.pleos.plugrest.vehicle.PleosVehicleGateway
import ai.pleos.plugrest.vehicle.SeatMode
import ai.pleos.plugrest.vehicle.VehicleGateway
import android.app.Application
import android.content.Context
import android.content.Intent

/** 앱 전역 객체 1벌. ponytail: DI 프레임워크 대신 Application이 들고 있는다. */
class Graph(context: Context) {
    val fake: Boolean = BuildConfig.FORCE_FAKE || !PleosVehicleGateway.isAvailable(context)
    val gateway: VehicleGateway = if (fake) FakeVehicleGateway() else PleosVehicleGateway(context)
    val narrator: Narrator = if (fake) AndroidNarrator(context) else PleosNarrator(context)
    val store = SessionStore(context)
    /** Activity가 보이는 동안은 서비스가 끝나도 게이트웨이를 유지 */
    @Volatile var activityVisible = false
    /** 선라이즈 램프 길이. 데모 모드: 3분 → 30초 */
    val sunriseMs = if (BuildConfig.DEMO) 30_000L else 3 * 60_000L
    val sunriseStepMs = if (BuildConfig.DEMO) 10_000L else Sunrise.STEP_MS
    val session = RestSession(
        rates = ChargeRateMemory(store.loadRates(), store::saveRates),
        calculatingMs = if (BuildConfig.DEMO) 20_000L else 3 * 60_000L,
        chargingWindowMs = if (BuildConfig.DEMO) 20_000L else 3 * 60_000L,
        minSlopeSpanMs = if (BuildConfig.DEMO) 40_000L else 2 * 60_000L,
        sunriseMs = sunriseMs,
    )

    init {
        gateway.start()
        store.load()?.let { (resting, snapshot) ->
            if (session.resume(resting, snapshot)) {
                try { RestService.start(context) } catch (e: Exception) { android.util.Log.w("PlugRest", "FGS restart", e) }
            } else store.clear()
        }
    }

    /** departMin > 0 이면 출발 시각 모드 */
    fun startRest(context: Context, target: Int, seatMode: SeatMode, departMin: Int = 0) {
        RestService.start(context, target, seatMode, departMin)
    }
}

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        graph = Graph(this)
    }

    companion object {
        lateinit var graph: Graph
            private set
    }
}
