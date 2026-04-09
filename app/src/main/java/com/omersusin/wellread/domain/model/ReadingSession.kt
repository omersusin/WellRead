package com.omersusin.wellread.domain.model

data class ReadingSession(
    val id: Long = 0,
    val bookId: Long,
    val mode: ReadingMode,
    val startTime: Long,
    val endTime: Long,
    val wordsRead: Int,
    val wpm: Int,
    val date: Long = System.currentTimeMillis()
)

/**
 * BIONIC        – Bold-prefix reading (left-anchored fixation)
 * FLASH         – RSVP one word at a time
 * CHUNK         – RSVP N words at a time (configurable chunk size)
 * FOCUS         – Single large word with context dimmed
 * PARAGRAPH     – One paragraph at a time, swipe through
 * TRAIN         – Cloze / fill-in-the-blank comprehension
 * SENTENCE_SWIPE – Sentence cards, swipe to advance
 */
enum class ReadingMode {
    BIONIC,
    FLASH,
    CHUNK,
    FOCUS,
    PARAGRAPH,
    TRAIN,
    SENTENCE_SWIPE
}
