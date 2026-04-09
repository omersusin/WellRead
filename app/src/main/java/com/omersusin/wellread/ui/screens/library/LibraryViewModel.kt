package com.omersusin.wellread.ui.screens.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omersusin.wellread.data.repository.BookRepository
import com.omersusin.wellread.domain.model.Book
import com.omersusin.wellread.domain.model.BookType
import com.omersusin.wellread.utils.DataStoreManager
import com.omersusin.wellread.utils.FileParser
import com.omersusin.wellread.utils.ParseResult
import com.omersusin.wellread.utils.WebImporter
import com.omersusin.wellread.utils.WebImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val allBooks: List<Book> = emptyList(),
    val filteredBooks: List<Book> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: BookFilter = BookFilter.ALL,
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
    val importError: String? = null,
    val showAddDialog: Boolean = false,
    val showUrlDialog: Boolean = false
)

enum class BookFilter { ALL, READING, FINISHED, PDF, EPUB, DOCX, WEB }

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val fileParser: FileParser,
    private val webImporter: WebImporter,
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            bookRepository.getAllBooks().collect { books ->
                _uiState.update { state ->
                    state.copy(
                        allBooks = books,
                        filteredBooks = filterBooks(books, state.searchQuery, state.selectedFilter)
                    )
                }
            }
        }
    }

    fun onSearchQuery(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredBooks = filterBooks(state.allBooks, query, state.selectedFilter)
            )
        }
    }

    fun onFilterSelected(filter: BookFilter) {
        _uiState.update { state ->
            state.copy(
                selectedFilter = filter,
                filteredBooks = filterBooks(state.allBooks, state.searchQuery, filter)
            )
        }
    }

    private fun filterBooks(books: List<Book>, query: String, filter: BookFilter): List<Book> =
        books
            .filter { book ->
                query.isBlank() ||
                book.title.contains(query, ignoreCase = true) ||
                book.author.contains(query, ignoreCase = true)
            }
            .filter { book ->
                when (filter) {
                    BookFilter.ALL      -> true
                    BookFilter.READING  -> !book.isFinished
                    BookFilter.FINISHED -> book.isFinished
                    BookFilter.PDF      -> book.type == BookType.PDF
                    BookFilter.EPUB     -> book.type == BookType.EPUB
                    BookFilter.DOCX     -> book.type == BookType.DOCX
                    BookFilter.WEB      -> book.type == BookType.WEB
                }
            }

    fun importFile(uri: Uri, mimeType: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, importError = null) }

            val uriLower = uri.toString().lowercase()
            val mime     = mimeType?.lowercase() ?: ""

            val isPdf  = mime.contains("pdf")     || uriLower.endsWith(".pdf")
            val isEpub = mime.contains("epub")    || uriLower.endsWith(".epub")
            val isDocx = mime.contains("wordprocessingml") ||
                         mime.contains("msword")  || uriLower.endsWith(".docx")
            val isHtml = mime.contains("html")    || uriLower.endsWith(".html") || uriLower.endsWith(".htm")
            val isMd   = uriLower.endsWith(".md") || uriLower.endsWith(".markdown")

            val result = when {
                isPdf  -> fileParser.parsePdf(uri)
                isEpub -> fileParser.parseEpub(uri)
                isDocx -> fileParser.parseDocx(uri)
                isHtml -> fileParser.parseHtml(uri)
                isMd   -> fileParser.parseMarkdown(uri)
                else   -> fileParser.parseTxt(uri)
            }

            when (result) {
                is ParseResult.Success -> {
                    val type = when {
                        isPdf  -> BookType.PDF
                        isEpub -> BookType.EPUB
                        isDocx -> BookType.DOCX
                        else   -> BookType.TXT
                    }
                    val rawName = uri.lastPathSegment
                        ?.substringAfterLast("/")
                        ?.substringAfterLast("%2F")
                        ?: "Imported Book"
                    val title = rawName
                        .removeSuffix(".pdf").removeSuffix(".epub").removeSuffix(".docx")
                        .removeSuffix(".txt").removeSuffix(".html").removeSuffix(".md")
                        .replace("%20", " ").trim()
                        .ifBlank { "Imported Book" }

                    bookRepository.insertBook(
                        Book(
                            title = title,
                            type  = type,
                            filePath   = uri.toString(),
                            totalWords = result.wordCount
                        )
                    )
                    _uiState.update { it.copy(isImporting = false) }
                }
                is ParseResult.Error -> {
                    _uiState.update { it.copy(isImporting = false, importError = result.message) }
                }
            }
        }
    }

    fun importFromUrl(url: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, importError = null, showUrlDialog = false) }
            when (val result = webImporter.importFromUrl(url)) {
                is WebImportResult.Success -> {
                    bookRepository.insertBook(
                        Book(
                            title      = result.title,
                            type       = BookType.WEB,
                            sourceUrl  = result.sourceUrl,
                            totalWords = result.wordCount
                        )
                    )
                    _uiState.update { it.copy(isImporting = false) }
                }
                is WebImportResult.Error -> {
                    _uiState.update { it.copy(isImporting = false, importError = result.message) }
                }
            }
        }
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch { bookRepository.deleteBook(book) }
    }

    fun showAddDialog()  = _uiState.update { it.copy(showAddDialog = true) }
    fun hideAddDialog()  = _uiState.update { it.copy(showAddDialog = false) }
    fun showUrlDialog()  = _uiState.update { it.copy(showUrlDialog = true, showAddDialog = false) }
    fun hideUrlDialog()  = _uiState.update { it.copy(showUrlDialog = false) }
    fun clearError()     = _uiState.update { it.copy(importError = null) }
}
