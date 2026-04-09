package com.omersusin.wellread.data.repository

import com.omersusin.wellread.data.local.dao.BookDao
import com.omersusin.wellread.data.local.entities.toEntity
import com.omersusin.wellread.domain.model.Book
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepository @Inject constructor(
    private val bookDao: BookDao
) {
    fun getAllBooks(): Flow<List<Book>> =
        bookDao.getAllBooks().map { list -> list.map { it.toDomain() } }

    fun getRecentBooks(): Flow<List<Book>> =
        bookDao.getRecentBooks().map { list -> list.map { it.toDomain() } }

    fun getFinishedBooks(): Flow<List<Book>> =
        bookDao.getFinishedBooks().map { list -> list.map { it.toDomain() } }

    suspend fun getBookById(id: Long): Book? =
        bookDao.getBookById(id)?.toDomain()

    suspend fun insertBook(book: Book): Long =
        bookDao.insertBook(book.toEntity())

    suspend fun updateBook(book: Book) =
        bookDao.updateBook(book.toEntity())

    suspend fun deleteBook(book: Book) =
        bookDao.deleteBook(book.toEntity())

    suspend fun updateProgress(id: Long, position: Int) =
        bookDao.updateProgress(id, position)

    suspend fun markFinished(id: Long) =
        bookDao.markFinished(id)
}
