package com.catsmoker.obd2ai.ui.dashboard

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.edit
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.ai.OnlineAiSeverity
import com.catsmoker.obd2ai.ai.QueuedSpeech
import com.catsmoker.obd2ai.prefs.PrefsKeys
import com.catsmoker.obd2ai.prefs.Units
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Offline/Online AI cue pipeline for [LiveSession]: text selection, cooldowns,
 * Easter eggs, TTS engines, banner/card timers and warning sounds. Split into
 * extension functions only to keep the session file focused on the polling
 * session; everything here operates on the same instance state.
 */

/** Per-event text with the optional detail suffix (codes, temp). */
internal fun LiveSession.offlineAiText(event: LiveSession.OfflineAiEvent, detail: String? = null): String {
    val text = when (event) {
        LiveSession.OfflineAiEvent.WELCOME -> activity.getString(R.string.offline_ai_online)
        LiveSession.OfflineAiEvent.SHIFT_POINT -> activity.getString(R.string.offline_ai_shift_up)
        LiveSession.OfflineAiEvent.HIGH_RPM -> activity.getString(R.string.offline_ai_high_rpm)
        LiveSession.OfflineAiEvent.HIGH_SPEED -> activity.getString(R.string.offline_ai_high_speed)
        LiveSession.OfflineAiEvent.COOLANT_HIGH -> {
            val value = coolantC.value ?: 0
            if (imperial) {
                activity.getString(
                    R.string.live_data_coolant_warning_f,
                    Units.displayTemp(value.toDouble(), true).toInt()
                )
            } else {
                activity.getString(R.string.live_data_coolant_warning, value)
            }
        }
        LiveSession.OfflineAiEvent.COOLANT_OK -> activity.getString(R.string.offline_ai_coolant_ok)
        LiveSession.OfflineAiEvent.VOLTAGE_LOW -> activity.getString(R.string.warn_voltage_low)
        LiveSession.OfflineAiEvent.VOLTAGE_CRITICAL -> activity.getString(R.string.warn_voltage_critical)
        LiveSession.OfflineAiEvent.FUEL_LOW -> activity.getString(R.string.warn_fuel_low)
        LiveSession.OfflineAiEvent.FUEL_CRITICAL -> activity.getString(R.string.warn_fuel_critical)
        LiveSession.OfflineAiEvent.NEW_FAULT_CODE -> activity.getString(R.string.offline_ai_new_fault)
        LiveSession.OfflineAiEvent.CONNECTION_LOST -> activity.getString(R.string.offline_ai_conn_lost)
        LiveSession.OfflineAiEvent.CONNECTION_RESTORED -> activity.getString(R.string.offline_ai_conn_restored)
        LiveSession.OfflineAiEvent.ENGINE_STARTED -> activity.getString(R.string.offline_ai_engine_start)
        LiveSession.OfflineAiEvent.ENGINE_STOPPED -> activity.getString(R.string.offline_ai_engine_stop)
    }
    return if (detail.isNullOrBlank()) text else "$text $detail"
}

internal fun LiveSession.fireOfflineAi(
    event: LiveSession.OfflineAiEvent,
    text: String,
    cooldownMs: Long = 0L
) {
    if (!offlineMasterOn()) return
    if (cooldownMs > 0L) {
        val now = System.currentTimeMillis()
        val last = offlineAiLastFired[event]
        if (last != null && !LiveLogic.offlineAiReady(last, now, cooldownMs)) return
        offlineAiLastFired[event] = now
    }
    emitOfflineAiCue(text)
}

internal fun LiveSession.fireOfflineAiOnce(event: LiveSession.OfflineAiEvent, text: String) {
    if (offlineAiWelcomed && event == LiveSession.OfflineAiEvent.WELCOME) return
    if (event == LiveSession.OfflineAiEvent.WELCOME) offlineAiWelcomed = true
    fireOfflineAi(event, text)
}

/** One cue reaches the user through both enabled outputs (voice + banner). */
internal fun LiveSession.emitOfflineAiCue(text: String) {
    if (offlineVoiceSwitchOn()) speakOfflineAi(text)
    if (offlineTextSwitchOn()) showOfflineAiBanner(text)
}

/** Speaks [text] with TTS at Offline AI volume, initializing on first use. */
internal fun LiveSession.speakOfflineAi(text: String, utterance: String = "offlineAi") {
    val params = Bundle().apply {
        putFloat(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_VOLUME, offlineVolume)
    }
    val engine = tts
    if (engine == null) {
        // First use: speak from the init callback once the engine is ready.
        tts = android.speech.tts.TextToSpeech(activity) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                runCatching { tts?.language = java.util.Locale.getDefault() }
                tts?.speak(
                    text,
                    android.speech.tts.TextToSpeech.QUEUE_FLUSH,
                    params,
                    utterance
                )
            } else {
                Log.e("LiveSession", "TTS init failed: $status")
            }
        }
    } else {
        engine.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, utterance)
    }
}

internal fun LiveSession.showOfflineAiBanner(
    text: String,
    actionLabel: String? = null,
    action: (() -> Unit)? = null
) {
    bannerJob?.cancel()
    val ms = LiveLogic.bannerDurationMs(text)
    banner.value = LiveBanner(
        id = System.currentTimeMillis(),
        text = text,
        actionLabel = actionLabel,
        action = action
    )
    bannerSeconds.value = (ms / 1000).toInt()
    bannerJob = scope.launch {
        var remaining = bannerSeconds.value
        while (remaining > 0) {
            delay(1000)
            remaining--
            bannerSeconds.value = remaining
        }
        banner.value = null
    }
}

internal fun LiveSession.skipOfflineAi() {
    bannerJob?.cancel()
    banner.value = null
    tts?.takeIf { it.isSpeaking }?.stop()
}

// -------------------------------------------------------- easter eggs

internal fun LiveSession.maybeFireEasterEgg(pool: String) {
    if (!easterOn || !offlineMasterOn()) return
    val lines = easterLines[pool].orEmpty()
    if (lines.isEmpty()) return
    val config = LiveLogic.easterPoolConfig(pool)
    val now = System.currentTimeMillis()
    val last = easterCooldowns[pool] ?: 0L
    if (!LiveLogic.offlineAiReady(last, now, config.cooldownMs)) return
    val ultraRare = LiveLogic.rollEasterEgg(
        easterRandom.nextFloat(),
        LiveLogic.easterPoolConfig(LiveLogic.POOL_ULTRA).probability
    )
    val probability = if (ultraRare) 1f else config.probability
    if (!LiveLogic.rollEasterEgg(easterRandom.nextFloat(), probability)) return
    val source = if (ultraRare) easterLines[LiveLogic.POOL_ULTRA].orEmpty().ifEmpty { lines } else lines
    val start = (easterRandom.nextFloat() * source.size).toInt().coerceIn(0, source.size - 1)
    val line = LiveLogic.pickFreshLine(source, easterRecent, start) ?: return
    easterCooldowns[pool] = now
    easterRecent.addLast(line)
    while (easterRecent.size > LiveLogic.EASTER_RECENT_MAX) easterRecent.removeFirst()
    val emit = { emitOfflineAiCue(line) }
    if (config.delayMs > 0L) {
        scope.launch {
            delay(config.delayMs)
            if (active) emit()
        }
    } else {
        emit()
    }
}

internal fun LiveSession.maybeComboEaster(rpmValue: Int) {
    if (!LiveLogic.comboActive(rpmValue, highRpmThreshold, lastSpeedKmh, highSpeedThreshold)) return
    maybeFireEasterEgg(LiveLogic.POOL_COMBO)
}

// ----------------------------------------------------------- online AI

internal fun LiveSession.aiVolume(): Float =
    (prefs.getInt(PrefsKeys.AI_VOLUME, PrefsKeys.DEFAULT_AI_VOLUME).coerceIn(0, 100)) / 100f

internal fun LiveSession.showOnlineAiCard(text: String) {
    onlineJob?.cancel()
    onlineCardText.value = text
    val seconds = (LiveLogic.bannerDurationMs(text) / 1000).toInt().coerceAtLeast(4)
    onlineSeconds.value = seconds
    onlineJob = scope.launch {
        var remaining = seconds
        while (remaining > 0) {
            delay(1000)
            remaining--
            onlineSeconds.value = remaining
        }
        onlineCardText.value = null
    }
}

/** Dismisses the Online AI card early and drops its queued voice. */
internal fun LiveSession.skipOnlineAi() {
    onlineJob?.cancel()
    onlineCardText.value = null
    speechQueue.clear()
    speakingSeverity = null
    stopSpeechPlayback()
}

/** Speaks an Online AI reply with the device voice; the dashboard always survives. */
internal fun LiveSession.speakOnlineAi(text: String, severity: OnlineAiSeverity) {
    if (muted.value) return // card already shown; voice stays silent
    val utterance = QueuedSpeech(text, severity)
    val current = speakingSeverity
    if (current != null) {
        if (severity.ordinal > current.ordinal) {
            // Higher severity preempts: drop the current remainder.
            stopSpeechPlayback()
            speakingSeverity = null
            scope.launch { playSpeech(utterance) }
        } else {
            speechQueue.offer(utterance)
        }
    } else {
        scope.launch { playSpeech(utterance) }
    }
}

internal fun LiveSession.playSpeech(utterance: QueuedSpeech) {
    speakingSeverity = utterance.severity
    if (!active) {
        speakingSeverity = null
        return
    }
    playDeviceVoice(utterance.text)
}

/** Device voice on its own engine so Offline AI cues can never flush it. */
internal fun LiveSession.playDeviceVoice(text: String) {
    val params = Bundle().apply {
        putFloat(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_VOLUME, aiVolume())
    }
    val engine = onlineAiTts
    if (engine == null) {
        onlineAiTts = android.speech.tts.TextToSpeech(activity) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                onlineAiTts?.setOnUtteranceProgressListener(speechProgressListener)
                runCatching { onlineAiTts?.language = java.util.Locale.getDefault() }
                onlineAiTts?.speak(
                    text,
                    android.speech.tts.TextToSpeech.QUEUE_FLUSH,
                    params,
                    "onlineai"
                )
            } else {
                Log.e("LiveSession", "Online AI TTS init failed: $status")
                Handler(Looper.getMainLooper()).post { onSpeechDone() }
            }
        }
    } else {
        engine.speak(
            text,
            android.speech.tts.TextToSpeech.QUEUE_FLUSH,
            params,
            "onlineai"
        )
    }
}


internal fun LiveSession.onSpeechDone() {
    speakingSeverity = null
    val next = speechQueue.poll()
    if (next != null && active) {
        scope.launch { playSpeech(next) }
    }
}

/** Stops whatever the Online AI is saying (preemption, mute, teardown). */
internal fun LiveSession.stopSpeechPlayback() {
    runCatching { onlineAiTts?.stop() }
}

// ------------------------------------------------------------- sounds

internal fun LiveSession.playSound(resId: Int) {
    if (muted.value) return
    mediaPlayer?.release()
    mediaPlayer = android.media.MediaPlayer.create(activity, resId)?.apply {
        setVolume(offlineVolume, offlineVolume)
        setOnCompletionListener { it.release() }
        start()
    }
}

internal fun LiveSession.stopSound() {
    mediaPlayer?.let { player ->
        runCatching { if (player.isPlaying) player.stop() }
        player.release()
    }
    mediaPlayer = null
}
