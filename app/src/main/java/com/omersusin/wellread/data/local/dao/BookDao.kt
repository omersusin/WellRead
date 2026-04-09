package com.omersusin.wellread.data.local.dao

import androidx.room.*
import com.omersusin.wellread.data.local.entities.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastReadAt DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE isFinished = 0 ORDER BY lastReadAt DESC LIMIT 5")
    fun getRecentBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE isFinished = 1 ORDER BY lastReadAt DESC")
    fun getFinishedBooks(): Flow<List<BookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long

    @Update
    suspend fun updateBook(book: BookEntity)

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Query("UPDATE books SET currentPosition = :position, lastReadAt = :time WHERE id = :id")
    suspend fun updateProgress(id: Long, position: Int, time: Long = System.currentTimeMillis())

    @Query("UPDATE books SET isFinished = 1 WHERE id = :id")
    suspend fun markFinished(id: Long)

    @Query("SELECT COUNT(*) FROM books")
    suspend fun getTotalBooks(): Int
}
