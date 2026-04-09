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

enum class ReadingMode {
    BIONIC,
    FLASH,
    FOCUS,
    TRAIN,
    SENTENCE_SWIPE
}
