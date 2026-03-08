package com.example.marathon.service

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject

class RunningTtsManager @Inject constructor(
    @ApplicationContext context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isReady = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.ENGLISH
            isReady = true
        }
    }

    fun announceKilometer(km: Int, pace: String, elapsedTime: String) {
        if (!isReady) return
        val message = "$km kilometer. Pace $pace per kilometer. Time $elapsedTime."
        tts?.speak(message, TextToSpeech.QUEUE_ADD, null, "km_$km")
    }

    fun announceRunStart() {
        if (!isReady) return
        tts?.speak("Run started. Good luck!", TextToSpeech.QUEUE_FLUSH, null, "start")
    }

    fun announceRunComplete(distanceKm: String, elapsedTime: String) {
        if (!isReady) return
        val message = "Run complete. $distanceKm kilometers in $elapsedTime. Great job!"
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "complete")
    }

    fun announcePause() {
        if (!isReady) return
        tts?.speak("Run paused.", TextToSpeech.QUEUE_FLUSH, null, "pause")
    }

    fun announceResume() {
        if (!isReady) return
        tts?.speak("Run resumed.", TextToSpeech.QUEUE_FLUSH, null, "resume")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
