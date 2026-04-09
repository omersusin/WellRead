package com.omersusin.wellread.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.omersusin.wellread.domain.model.Book
import com.omersusin.wellread.domain.model.BookType

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val author: String = "Unknown",
    val filePath: String = "",
    val sourceUrl: String = "",
    val coverPath: String = "",
    val type: String,
    val totalWords: Int = 0,
    val currentPosition: Int = 0,
    val isFinished: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long = 0L,
    val content: String = ""
) {
    fun toDomain() = Book(
        id = id,
        title = title,
        author = author,
        filePath = filePath,
        sourceUrl = sourceUrl,
        coverPath = coverPath,
        type = try { BookType.valueOf(type) } catch (_: Exception) { BookType.TXT },
        totalWords = totalWords,
        currentPosition = currentPosition,
        isFinished = isFinished,
        addedAt = addedAt,
        lastReadAt = lastReadAt,
        content = content
    )
}

fun Book.toEntity() = BookEntity(
    id = id,
    title = title,
    author = author,
    filePath = filePath,
    sourceUrl = sourceUrl,
    coverPath = coverPath,
    type = type.name,
    totalWords = totalWords,
    currentPosition = currentPosition,
    isFinished = isFinished,
    addedAt = addedAt,
    lastReadAt = lastReadAt,
    content = content
)
