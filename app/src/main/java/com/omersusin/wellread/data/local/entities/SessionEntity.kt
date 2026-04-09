package com.omersusin.wellread.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.omersusin.wellread.domain.model.ReadingMode
import com.omersusin.wellread.domain.model.ReadingSession

@Entity(
    tableName = "reading_sessions",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val mode: String,
    val startTime: Long,
    val endTime: Long,
    val wordsRead: Int,
    val wpm: Int,
    val date: Long = System.currentTimeMillis()
) {
    fun toDomain() = ReadingSession(
        id = id,
        bookId = bookId,
        mode = ReadingMode.valueOf(mode),
        startTime = startTime,
        endTime = endTime,
        wordsRead = wordsRead,
        wpm = wpm,
        date = date
    )
}

fun ReadingSession.toEntity() = SessionEntity(
    id = id,
    bookId = bookId,
    mode = mode.name,
    startTime = startTime,
    endTime = endTime,
    wordsRead = wordsRead,
    wpm = wpm,
    date = date
)
