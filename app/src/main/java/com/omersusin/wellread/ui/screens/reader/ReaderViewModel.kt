package com.omersusin.wellread.ui.screens.reader

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omersusin.wellread.data.repository.BookRepository
import com.omersusin.wellread.data.repository.SessionRepository
import com.omersusin.wellread.domain.model.Book
import com.omersusin.wellread.domain.model.BookType
import com.omersusin.wellread.domain.model.ReadingMode
import com.omersusin.wellread.domain.model.ReadingSession
import com.omersusin.wellread.domain.model.UserPreferences
import com.omersusin.wellread.utils.DataStoreManager
import com.omersusin.wellread.utils.FileParser
import com.omersusin.wellread.utils.ParseResult
import com.omersusin.wellread.utils.TextProcessor
import com.omersusin.wellread.utils.WebImporter
import com.omersusin.wellread.utils.WebImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReaderUiState(
    val book: Book? = null,
    val fullText: String = "",
    val words: List<String> = emptyList(),
    val sentences: List<String> = emptyList(),
    val paragraphs: List<String> = emptyList(),
    val currentMode: ReadingMode = ReadingMode.BIONIC,
    val isPlaying: Boolean = false,
    val currentWordIndex: Int = 0,
    val currentSentenceIndex: Int = 0,
    val currentParagraphIndex: Int = 0,
    val wpm: Int = 300,
    val chunkSize: Int = 3,
    val isLoading: Boolean = true,
    val error: String? = null,
    val preferences: UserPreferences = UserPreferences(),
    val sessionStartTime: Long = 0L,
    val showModeSelector: Boolean = false,
    val showSettings: Boolean = false,
    val fontSize: Float = 16f
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val sessionRepository: SessionRepository,
    private val fileParser: FileParser,
    private val webImporter: WebImporter,
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var playJob: Job? = null
    private var sessionStartTime = 0L
    private var lastSavedIndex = 0

    fun initialize(bookId: Long, initialMode: String) {
        viewModelScope.launch {
            val prefs = dataStoreManager.userPreferences.first()
            _uiState.update {
                it.copy(
                    preferences = prefs,
                    wpm = prefs.defaultWpm,
                    fontSize = prefs.fontSize,
                    currentMode = try { ReadingMode.valueOf(initialMode) } catch (_: Exception) { prefs.defaultMode }
                )
            }
            loadBook(bookId)
        }
    }

    private suspend fun loadBook(bookId: Long) {
        _uiState.update { it.copy(isLoading = true, error = null) }

        val book = bookRepository.getBookById(bookId)
        if (book == null) {
            _uiState.update { it.copy(isLoading = false, error = "Book not found") }
            return
        }
        _uiState.update { it.copy(book = book) }

        val result: ParseResult = when (book.type) {
            BookType.WEB -> {
                // Use cached content if available; only re-fetch if missing
                if (book.content.isNotBlank()) {
                    ParseResult.Success(book.content, TextProcessor.countWords(book.content))
                } else if (book.sourceUrl.isNotBlank()) {
                    when (val r = webImporter.importFromUrl(book.sourceUrl)) {
                        is WebImportResult.Success -> {
                            // Back-fill cache so next open is instant
                            bookRepository.updateBook(book.copy(content = r.text))
                            ParseResult.Success(r.text, TextProcessor.countWords(r.text))
                        }
                        is WebImportResult.Error -> ParseResult.Error(r.message)
                    }
                } else {
                    ParseResult.Error("No content or URL stored for this book")
                }
            }
            BookType.CLIPBOARD -> {
                if (book.content.isNotBlank())
                    ParseResult.Success(book.content, TextProcessor.countWords(book.content))
                else
                    ParseResult.Error("Clipboard content is empty")
            }
            BookType.PDF      -> if (book.content.isNotBlank()) ParseResult.Success(book.content, TextProcessor.countWords(book.content)) else fetchFileText(book.filePath, "PDF")
            BookType.EPUB     -> if (book.content.isNotBlank()) ParseResult.Success(book.content, TextProcessor.countWords(book.content)) else fetchFileText(book.filePath, "EPUB")
            BookType.TXT      -> if (book.content.isNotBlank()) ParseResult.Success(book.content, TextProcessor.countWords(book.content)) else fetchFileText(book.filePath, "TXT")
            BookType.DOCX     -> if (book.content.isNotBlank()) ParseResult.Success(book.content, TextProcessor.countWords(book.content)) else fetchFileText(book.filePath, "DOCX")
            BookType.HTML     -> if (book.content.isNotBlank()) ParseResult.Success(book.content, TextProcessor.countWords(book.content)) else fetchFileText(book.filePath, "HTML")
            BookType.MARKDOWN -> if (book.content.isNotBlank()) ParseResult.Success(book.content, TextProcessor.countWords(book.content)) else fetchFileText(book.filePath, "MD")
        }

        when (result) {
            is ParseResult.Error -> {
                _uiState.update { it.copy(isLoading = false, error = result.message) }
            }
            is ParseResult.Success -> {
                val text      = result.text
                val words     = TextProcessor.splitIntoWords(text)
                val sentences = TextProcessor.splitIntoSentences(text)
                val paragraphs = text.split(Regex("\\n{2,}"))
                    .map { it.trim() }.filter { it.length > 10 }

                if (words.isEmpty()) {
                    _uiState.update { it.copy(isLoading = false, error = "No readable text found in this file.") }
                    return
                }

                val startWord = book.currentPosition.coerceIn(0, (words.size - 1).coerceAtLeast(0))
                sessionStartTime = System.currentTimeMillis()

                _uiState.update {
                    it.copy(
                        fullText = text,
                        words = words,
                        sentences = sentences,
                        paragraphs = paragraphs,
                        currentWordIndex = startWord,
                        isLoading = false,
                        error = null,
                        sessionStartTime = sessionStartTime
                    )
                }
            }
        }
    }

    private suspend fun fetchFileText(path: String, type: String): ParseResult {
        if (path.isBlank()) return ParseResult.Error("No file path stored for this book")
        return try {
            val uri = Uri.parse(path)
            when (type) {
                "PDF"  -> fileParser.parsePdf(uri)
                "EPUB" -> fileParser.parseEpub(uri)
                "DOCX" -> fileParser.parseDocx(uri)
                "HTML" -> fileParser.parseHtml(uri)
                "MD"   -> fileParser.parseMarkdown(uri)
                "RTF"  -> fileParser.parseRtf(uri)
                else   -> fileParser.parseTxt(uri)
            }
        } catch (e: SecurityException) {
            ParseResult.Error(
                "File access denied.\n\n" +
                "The app no longer has permission to read this file.\n" +
                "Please re-import the file from the Library."
            )
        } catch (e: Exception) {
            ParseResult.Error("Could not read file: ${e.message ?: "Unknown error"}")
        }
    }

    fun togglePlay() {
        val playing = !_uiState.value.isPlaying
        _uiState.update { it.copy(isPlaying = playing) }
        if (playing) startAutoPlay() else stopAutoPlay()
    }

    private fun startAutoPlay() {
        playJob?.cancel()
        playJob = viewModelScope.launch {
            when (_uiState.value.currentMode) {
                ReadingMode.FLASH, ReadingMode.FOCUS -> autoPlayWords()
                ReadingMode.CHUNK                    -> autoPlayChunks()
                else -> {}
            }
        }
    }

    private suspend fun autoPlayWords() {
        while (_uiState.value.isPlaying) {
            val state = _uiState.value
            if (state.currentWordIndex >= state.words.size - 1) {
                _uiState.update { it.copy(isPlaying = false) }
                saveSession(); break
            }
            val delayMs = (60000L / state.wpm).coerceAtLeast(50L)
            delay(delayMs)
            _uiState.update { it.copy(currentWordIndex = it.currentWordIndex + 1) }
            saveProgressPeriodically()
        }
    }

    private suspend fun autoPlayChunks() {
        while (_uiState.value.isPlaying) {
            val state = _uiState.value
            val nextIndex = state.currentWordIndex + state.chunkSize
            if (nextIndex >= state.words.size) {
                _uiState.update { it.copy(isPlaying = false) }
                saveSession(); break
            }
            // Display time scales with chunk size and WPM
            val msPerWord = (60000L / state.wpm).coerceAtLeast(50L)
            delay(msPerWord * state.chunkSize)
            _uiState.update { it.copy(currentWordIndex = nextIndex) }
            saveProgressPeriodically()
        }
    }

    private fun stopAutoPlay() { playJob?.cancel(); playJob = null }

    fun nextWord() {
        _uiState.update { state ->
            if (state.currentWordIndex < state.words.size - 1)
                state.copy(currentWordIndex = state.currentWordIndex + 1)
            else state
        }
    }

    fun previousWord() {
        _uiState.update { state ->
            if (state.currentWordIndex > 0) state.copy(currentWordIndex = state.currentWordIndex - 1) else state
        }
    }

    fun nextSentence() {
        _uiState.update { state ->
            if (state.currentSentenceIndex < state.sentences.size - 1)
                state.copy(currentSentenceIndex = state.currentSentenceIndex + 1)
            else state
        }
    }

    fun previousSentence() {
        _uiState.update { state ->
            if (state.currentSentenceIndex > 0) state.copy(currentSentenceIndex = state.currentSentenceIndex - 1) else state
        }
    }

    fun nextParagraph() {
        _uiState.update { state ->
            if (state.currentParagraphIndex < state.paragraphs.size - 1)
                state.copy(currentParagraphIndex = state.currentParagraphIndex + 1)
            else state
        }
    }

    fun previousParagraph() {
        _uiState.update { state ->
            if (state.currentParagraphIndex > 0) state.copy(currentParagraphIndex = state.currentParagraphIndex - 1) else state
        }
    }

    fun onSentenceSwiped() = nextSentence()

    fun setWpm(wpm: Int) { _uiState.update { it.copy(wpm = wpm.coerceIn(50, 1500)) } }

    fun setChunkSize(size: Int) { _uiState.update { it.copy(chunkSize = size.coerceIn(1, 10)) } }

    fun setMode(mode: ReadingMode) {
        stopAutoPlay()
        _uiState.update { it.copy(currentMode = mode, isPlaying = false, showModeSelector = false) }
    }

    fun setFontSize(size: Float) { _uiState.update { it.copy(fontSize = size.coerceIn(12f, 36f)) } }

    fun toggleModeSelector() { _uiState.update { it.copy(showModeSelector = !it.showModeSelector) } }
    fun toggleSettings()     { _uiState.update { it.copy(showSettings = !it.showSettings) } }

    fun seekTo(index: Int) {
        _uiState.update { it.copy(currentWordIndex = index.coerceIn(0, (it.words.size - 1).coerceAtLeast(0))) }
    }

    fun retryLoad() {
        val book = _uiState.value.book
        if (book != null) viewModelScope.launch { loadBook(book.id) }
    }

    private fun saveProgressPeriodically() {
        val state = _uiState.value
        if (state.currentWordIndex - lastSavedIndex > 100) {
            lastSavedIndex = state.currentWordIndex
            viewModelScope.launch { state.book?.let { bookRepository.updateProgress(it.id, state.currentWordIndex) } }
        }
    }

    private fun saveSession() {
        val state = _uiState.value
        val book = state.book ?: return
        val endTime = System.currentTimeMillis()
        val durationMs = endTime - sessionStartTime
        if (durationMs < 5000) return
        val wordsRead = state.currentWordIndex
        val minutes = durationMs / 60000.0
        val wpm = if (minutes > 0) (wordsRead / minutes).toInt() else 0
        viewModelScope.launch {
            sessionRepository.insertSession(
                ReadingSession(
                    bookId = book.id, mode = state.currentMode,
                    startTime = sessionStartTime, endTime = endTime,
                    wordsRead = wordsRead, wpm = wpm
                )
            )
            bookRepository.updateProgress(book.id, state.currentWordIndex)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAutoPlay()
        saveSession()
    }
}
