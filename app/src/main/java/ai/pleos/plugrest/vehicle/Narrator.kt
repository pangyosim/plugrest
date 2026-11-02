package ai.pleos.plugrest.vehicle

import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.util.Log
import java.util.Locale

interface Narrator {
    fun speak(text: String)
    fun release()
}

/** Pleos TextToSpeech 3.3.1, ON_DEVICE(앱 등록 불필요), ko-KR. 준비 전 요청은 1건만 보관했다가 재생. */
class PleosNarrator(context: Context) : Narrator {
    private val tts = ai.pleos.playground.tts.TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
    @Volatile private var ready = false
    @Volatile private var pending: String? = null

    init {
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                tts.setLocale(Locale.KOREA)
                ready = true
                pending?.let { speak(it) }; pending = null
            }
            override fun onFailure(errorType: ErrorType) { Log.w(TAG, "TTS init failed: $errorType") }
        })
    }

    override fun speak(text: String) {
        if (!ready) { pending = text; return }
        try { tts.speak(text, true) } catch (e: Exception) { Log.w(TAG, "speak", e) }
    }

    override fun release() { try { tts.release() } catch (_: Exception) {} }

    private companion object { const val TAG = "PleosNarrator" }
}

/** Fake 모드: 플랫폼 TTS로 같은 문장을 읽는다(엔진 없으면 로그만). */
class AndroidNarrator(context: Context) : Narrator {
    private var ready = false
    private lateinit var tts: android.speech.tts.TextToSpeech

    init {
        tts = android.speech.tts.TextToSpeech(context.applicationContext) { status ->
            ready = status == android.speech.tts.TextToSpeech.SUCCESS
            if (ready) tts.language = Locale.KOREAN
        }
    }

    override fun speak(text: String) {
        Log.i("Narrator", text)
        if (ready) tts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "plugrest")
    }

    override fun release() = tts.shutdown()
}
