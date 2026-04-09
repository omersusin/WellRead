package com.omersusin.wellread.data.repository

import com.omersusin.wellread.data.local.dao.SessionDao
import com.omersusin.wellread.data.local.entities.toEntity
import com.omersusin.wellread.domain.model.ReadingMode
import com.omersusin.wellread.domain.model.ReadingSession
import com.omersusin.wellread.domain.model.ReadingStats
import com.omersusin.wellread.domain.model.WpmDataPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao
) {
    fun getAllSessions(): Flow<List<ReadingSession>> =
        sessionDao.getAllSessions().map { list -> list.map { it.toDomain() } }

    fun getSessionsForBook(bookId: Long): Flow<List<ReadingSession>> =
        sessionDao.getSessionsForBook(bookId).map { list -> list.map { it.toDomain() } }

    suspend fun insertSession(session: ReadingSession): Long =
        sessionDao.insertSession(session.toEntity())

    suspend fun getStats(): ReadingStats {
        val totalWords = sessionDao.getTotalWordsRead() ?: 0
        val totalMs = sessionDao.getTotalTimeMs() ?: 0L
        val totalMinutes = (totalMs / 60000).toInt()
        val avgWpm = sessionDao.getAverageWpm()?.toInt() ?: 0

        val sessionsPerMode = ReadingMode.values().associateWith { mode ->
            sessionDao.getSessionCountForMode(mode.name)
        }

        val recentSessions = sessionDao.getRecentSessions(30)
        val wpmHistory = recentSessions
            .filter { it.wpm > 0 }
            .map { WpmDataPoint(it.date, it.wpm, ReadingMode.valueOf(it.mode)) }

        val streak = calculateStreak()

        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val todaySessions = sessionDao.getTodaySessions(todayStart)
        val todayMinutes = todaySessions.sumOf { (it.endTime - it.startTime) / 60000 }.toInt()

        return ReadingStats(
            totalWordsRead = totalWords,
            totalMinutesRead = totalMinutes,
            averageWpm = avgWpm,
            currentStreak = streak,
            longestStreak = streak,
            sessionsPerMode = sessionsPerMode,
            wpmHistory = wpmHistory,
            todayMinutes = todayMinutes
        )
    }

    private suspend fun calculateStreak(): Int {
        val days = sessionDao.getDistinctReadingDays()
        if (days.isEmpty()) return 0
        var streak = 1
        for (i in 0 until days.size - 1) {
            streak++
        }
        return streak
    }
}
