package com.maoneart.ioskeyboard.dictation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class VoiceDictationManager(
    private val context: Context,
    private val onPartialResult: (String) -> Unit,
    private val onFinalResult: (String) -> Unit,
    private val onStatusChange: (Boolean, String) -> Unit
) {

    private var speechRecognizer: SpeechRecognizer? = null
    var isListening: Boolean = false
        private set

    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening(languageCode: String = "id") {
        if (isListening) {
            stopListening()
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                        onStatusChange(true, "Mendengarkan suara...")
                    }

                    override fun onBeginningOfSpeech() {
                        onStatusChange(true, "Merekam ucapan...")
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        onStatusChange(false, "Memproses ucapan...")
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        val errorMsg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Masalah audio mic"
                            SpeechRecognizer.ERROR_CLIENT -> "Klien suara sibuk"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Izin mikrofon belum aktif"
                            SpeechRecognizer.ERROR_NETWORK -> "Periksa koneksi internet"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Koneksi suara timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "Tidak ada suara terdeteksi"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Layanan suara sibuk"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Waktu bicara habis"
                            else -> "Gagal merekam suara"
                        }
                        onStatusChange(false, errorMsg)
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val recognized = matches[0]
                            onFinalResult(recognized)
                            onStatusChange(false, "Selesai")
                        } else {
                            onStatusChange(false, "")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            onPartialResult(matches[0])
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val targetLocale = if (languageCode == "en") "en-US" else "id-ID"
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, targetLocale)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            speechRecognizer?.startListening(intent)
        } catch (t: Throwable) {
            Log.e("VoiceDictationManager", "Error starting voice dictation", t)
            isListening = false
            onStatusChange(false, "Fitur suara tidak tersedia di perangkat ini")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Throwable) {}
        isListening = false
        onStatusChange(false, "")
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Throwable) {}
        isListening = false
    }
}
