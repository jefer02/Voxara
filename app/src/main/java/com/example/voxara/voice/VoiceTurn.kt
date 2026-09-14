package com.example.voxara.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import com.example.voxara.core.advice.AdviceGrammar
import com.example.voxara.core.advice.IntentRouter
import com.example.voxara.core.advice.VoiceIntent
import com.example.voxara.core.format.formatHeadroom
import com.example.voxara.core.format.formatTwa
import com.example.voxara.core.scene.Scene
import com.example.voxara.core.scene.environmentLabel
import com.example.voxara.data.ExposureState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import kotlin.math.roundToInt

/**
 * VOICE TURN — 5 steps, target < 900 ms to first word.
 *
 *  01 Wake & arm      the wave appears before the first syllable; latency is hidden by motion
 *  02 Capture         dose sampling is unaffected; the turn's own audio is excluded from the ledger
 *  03 Intent          on-device ASR -> 6 intents; unmatched utterances fall through to advice
 *  04 Ground          the state struct is injected: Leq, dose, headroom, scene. Never a guess.
 *  05 Answer          TTS plus a one-line card; a mode change is already applied when speech starts
 *
 * PRIVACY: the mic indicator is on for the whole turn and session audio is discarded at the end.
 */
class VoiceTurn(private val context: Context) {

    enum class Phase { IDLE, LISTENING, THINKING, ANSWERED, UNAVAILABLE }

    data class Turn(
        val phase: Phase = Phase.IDLE,
        val amplitude: Float = 0f,
        val heard: String = "",
        val answer: String = "",
    )

    private val _turn = MutableStateFlow(Turn())
    val turn: StateFlow<Turn> = _turn.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    /** Applied before the sentence starts, so a mode change never lags the answer. */
    var onModeRequest: ((String) -> Unit)? = null

    fun prepare() {
        if (tts == null) {
            tts = TextToSpeech(context) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) tts?.language = Locale.getDefault()
            }
        }
    }

    fun release() {
        recognizer?.destroy()
        recognizer = null
        tts?.shutdown()
        tts = null
    }

    fun start(state: ExposureState) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            // Honest failure: no ASR on this device, so offer the grounded answer anyway.
            answer(state, VoiceIntent.ADVICE, heard = "")
            return
        }
        _turn.update { Turn(phase = Phase.LISTENING) }

        val r = buildRecognizer()
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit

            // The wave is your voice, not a loading spinner.
            override fun onRmsChanged(rmsdB: Float) {
                val a = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                _turn.update { it.copy(amplitude = a) }
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                _turn.update { it.copy(phase = Phase.THINKING) }
            }

            override fun onError(error: Int) {
                answer(state, VoiceIntent.ADVICE, heard = "")
            }

            override fun onResults(results: Bundle?) {
                val heard = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                answer(state, IntentRouter.route(heard), heard)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val heard = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (heard.isNotBlank()) _turn.update { it.copy(heard = heard) }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        runCatching { r.startListening(intent) }.onFailure {
            answer(state, VoiceIntent.ADVICE, heard = "")
        }
    }

    fun stop() {
        runCatching { recognizer?.stopListening() }
        _turn.update { it.copy(phase = Phase.IDLE, amplitude = 0f) }
    }

    private fun buildRecognizer(): SpeechRecognizer =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }

    /**
     * 04 + 05. Every figure in the reply comes from the dose engine — the model, when one is
     * reachable, is only ever allowed to choose the wording.
     */
    private fun answer(state: ExposureState, intent: VoiceIntent, heard: String) {
        val scene = if (state.scene == Scene.UNKNOWN) Scene.UNKNOWN else state.scene
        val sentence = when (intent) {
            VoiceIntent.LEVEL ->
                "${state.dba.roundToInt()} decibels, ${environmentLabel(state.dba).lowercase()}."

            VoiceIntent.HEADROOM ->
                if (state.dba < 80) "Nothing is accruing. Stay as long as you like."
                else "${formatHeadroom(state.headroomMinutes)} left at this level."

            VoiceIntent.HISTORY ->
                "Today: ${state.dosePercent.roundToInt()}% dose, TWA ${formatTwa(state.twaDba)}."

            VoiceIntent.MODE -> {
                val wantsConcert = heard.lowercase().let {
                    it.contains("concert") || it.contains("club") || it.contains("start")
                }
                onModeRequest?.invoke(if (wantsConcert) "CONCERT" else "URBAN")
                if (wantsConcert) "Concert mode on. Six hour cap."
                else "Back to urban mode. I'll stay quiet."
            }

            VoiceIntent.CALIBRATE ->
                "Calibration is in settings. Adjust until it matches a reference meter."

            VoiceIntent.ADVICE, VoiceIntent.UNKNOWN -> AdviceGrammar.advise(
                AdviceGrammar.State(
                    dba = state.dba,
                    dosePercent = state.dosePercent,
                    headroomMinutes = state.headroomMinutes,
                    scene = scene,
                    risk = state.risk,
                )
            )
        }

        _turn.update {
            it.copy(phase = Phase.ANSWERED, heard = heard, answer = sentence, amplitude = 0f)
        }
        if (ttsReady) {
            tts?.speak(sentence, TextToSpeech.QUEUE_FLUSH, null, "voxara-turn")
        }
        runCatching { recognizer?.cancel() }
    }
}
