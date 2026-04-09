package com.omersusin.wellread.data.local.dao

import androidx.room.*
import com.omersusin.wellread.data.local.entities.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM reading_sessions ORDER BY date DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM reading_sessions WHERE bookId = :bookId ORDER BY date DESC")
    fun getSessionsForBook(bookId: Long): Flow<List<SessionEntity>>

    @Query("SELECT * FROM reading_sessions WHERE date >= :from AND date <= :to ORDER BY date ASC")
    fun getSessionsInRange(from: Long, to: Long): Flow<List<SessionEntity>>

    @Query("SELECT SUM(wordsRead) FROM reading_sessions")
    suspend fun getTotalWordsRead(): Int?

    @Query("SELECT SUM(endTime - startTime) FROM reading_sessions")
    suspend fun getTotalTimeMs(): Long?

    @Query("SELECT AVG(wpm) FROM reading_sessions WHERE wpm > 0")
    suspend fun getAverageWpm(): Float?

    @Query("SELECT AVG(wpm) FROM reading_sessions WHERE mode = :mode AND wpm > 0")
    suspend fun getAverageWpmForMode(mode: String): Float?

    @Query("SELECT COUNT(*) FROM reading_sessions WHERE mode = :mode")
    suspend fun getSessionCountForMode(mode: String): Int

    @Query("SELECT * FROM reading_sessions ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentSessions(limit: Int = 30): List<SessionEntity>

    @Query("SELECT DISTINCT date(date/1000, 'unixepoch') FROM reading_sessions ORDER BY date DESC")
    suspend fun getDistinctReadingDays(): List<String>

    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Delete
    suspend fun deleteSession(session: SessionEntity)

    @Query("SELECT * FROM reading_sessions WHERE date >= :todayStart")
    suspend fun getTodaySessions(todayStart: Long): List<SessionEntity>
}
