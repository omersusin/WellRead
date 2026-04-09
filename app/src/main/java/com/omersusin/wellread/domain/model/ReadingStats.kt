package com.omersusin.wellread.domain.model

data class ReadingStats(
    val totalWordsRead: Int = 0,
    val totalMinutesRead: Int = 0,
    val averageWpm: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val sessionsPerMode: Map<ReadingMode, Int> = emptyMap(),
    val wpmHistory: List<WpmDataPoint> = emptyList(),
    val dailyGoalMinutes: Int = 20,
    val todayMinutes: Int = 0
)

data class WpmDataPoint(
    val date: Long,
    val wpm: Int,
    val mode: ReadingMode
)
