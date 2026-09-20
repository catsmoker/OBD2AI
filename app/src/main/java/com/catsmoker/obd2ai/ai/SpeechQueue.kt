package com.catsmoker.obd2ai.ai

/** One queued spoken reply. */
data class QueuedSpeech(
    val text: String,
    val severity: OnlineAiSeverity
)

/**
 * Tiny bounded voice queue: FAULT occasions evict queued INFO lines, and an
 * over-full queue drops the lowest severity first — never the newest FAULT.
 */
class SpeechQueue(private val capacity: Int = 3) {
    private val items = ArrayDeque<QueuedSpeech>()

    val size: Int get() = items.size

    fun offer(utterance: QueuedSpeech) {
        if (utterance.severity == OnlineAiSeverity.FAULT) {
            items.removeAll { it.severity == OnlineAiSeverity.INFO }
        }
        items.addLast(utterance)
        while (items.size > capacity) {
            val dropAt = items.indices.minByOrNull { items[it].severity.ordinal } ?: 0
            items.removeAt(dropAt)
        }
    }

    fun poll(): QueuedSpeech? = items.removeFirstOrNull()

    fun clear() {
        items.clear()
    }
}
