package com.omersusin.wellread.ui.screens.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omersusin.wellread.data.repository.BookRepository
import com.omersusin.wellread.data.repository.SessionRepository
import com.omersusin.wellread.domain.model.Book
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
    val currentMode: ReadingMode = ReadingMode.BIONIC,
    val isPlaying: Boolean = false,
    val currentWordIndex: Int = 0,
    val currentSentenceIndex: Int = 0,
    val wpm: Int = 300,
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

    fun initialize(bookId: Long, initialMode: String) {
        viewModelScope.launch {
            dataStoreManager.userPreferences.first().let { prefs ->
                _uiState.update {
                    it.copy(
                        preferences = prefs,
                        wpm = prefs.defaultWpm,
                        fontSize = prefs.fontSize,
                        currentMode = try {
                            ReadingMode.valueOf(initialMode)
                        } catch (e: Exception) {
                            prefs.defaultMode
                        }
                    )
                }
            }
            loadBook(bookId)
        }
    }

    private suspend fun loadBook(bookId: Long) {
        val book = bookRepository.getBookById(bookId)
        if (book == null) {
            _uiState.update { it.copy(isLoading = false, error = "Book not found") }
            return
        }
        _uiState.update { it.copy(book = book) }

        val text = when {
            book.sourceUrl.isNotBlank() -> fetchWebText(book.sourceUrl)
            book.filePath.isNotBlank() -> fetchFileText(book.filePath, book.type.name)
            else -> ""
        }

        if (text.isBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "Could not load content") }
            return
        }

        val words = TextProcessor.splitIntoWords(text)
        val sentences = TextProcessor.splitIntoSentences(text)
        val startWord = book.currentPosition.coerceIn(0, words.size - 1)

        _uiState.update {
            it.copy(
                fullText = text,
                words = words,
                sentences = sentences,
                currentWordIndex = startWord,
                isLoading = false
            )
        }
        sessionStartTime = System.currentTimeMillis()
        _uiState.update { it.copy(sessionStartTime = sessionStartTime) }
    }

    private suspend fun fetchWebText(url: String): String {
        return when (val result = webImporter.importFromUrl(url)) {
            is WebImportResult.Success -> result.text
            is WebImportResult.Error -> ""
        }
    }

    private suspend fun fetchFileText(path: String, type: String): String {
        val uri = android.net.Uri.parse(path)
        return when (val result = if (type == "PDF") fileParser.parsePdf(uri) else fileParser.parseTxt(uri)) {
            is ParseResult.Success -> result.text
            is ParseResult.Error -> ""
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
            val state = _uiState.value
            when (state.currentMode) {
                ReadingMode.FLASH -> autoPlayFlash()
                ReadingMode.FOCUS -> autoPlayFocus()
                else -> {}
            }
        }
    }

    private suspend fun autoPlayFlash() {
        while (_uiState.value.isPlaying) {
            val state = _uiState.value
            if (state.currentWordIndex >= state.words.size - 1) {
                _uiState.update { it.copy(isPlaying = false) }
                saveSession()
                break
            }
            val delayMs = (60000L / state.wpm)
            delay(delayMs)
            _uiState.update { it.copy(currentWordIndex = it.currentWordIndex + 1) }
            saveProgressPeriodically()
        }
    }

    private suspend fun autoPlayFocus() {
        while (_uiState.value.isPlaying) {
            val state = _uiState.value
            if (state.currentWordIndex >= state.words.size - 1) {
                _uiState.update { it.copy(isPlaying = false) }
                saveSession()
                break
            }
            val delayMs = (60000L / state.wpm)
            delay(delayMs)
            _uiState.update { it.copy(currentWordIndex = it.currentWordIndex + 1) }
            saveProgressPeriodically()
        }
    }

    private fun stopAutoPlay() {
        playJob?.cancel()
        playJob = null
    }

    fun nextWord() {
        _uiState.update { state ->
            if (state.currentWordIndex < state.words.size - 1)
                state.copy(currentWordIndex = state.currentWordIndex + 1)
            else state
        }
    }

    fun previousWord() {
        _uiState.update { state ->
            if (state.currentWordIndex > 0)
                state.copy(currentWordIndex = state.currentWordIndex - 1)
            else state
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
            if (state.currentSentenceIndex > 0)
                state.copy(currentSentenceIndex = state.currentSentenceIndex - 1)
            else state
        }
    }

    fun onSentenceSwiped() = nextSentence()

    fun setWpm(wpm: Int) {
        _uiState.update { it.copy(wpm = wpm.coerceIn(50, 1000)) }
    }

    fun setMode(mode: ReadingMode) {
        stopAutoPlay()
        _uiState.update { it.copy(currentMode = mode, isPlaying = false, showModeSelector = false) }
    }

    fun setFontSize(size: Float) {
        _uiState.update { it.copy(fontSize = size.coerceIn(12f, 32f)) }
    }

    fun toggleModeSelector() {
        _uiState.update { it.copy(showModeSelector = !it.showModeSelector) }
    }

    fun toggleSettings() {
        _uiState.update { it.copy(showSettings = !it.showSettings) }
    }

    fun seekTo(index: Int) {
        _uiState.update { it.copy(currentWordIndex = index.coerceIn(0, it.words.size - 1)) }
    }

    private var lastSavedIndex = 0
    private fun saveProgressPeriodically() {
        val state = _uiState.value
        if (state.currentWordIndex - lastSavedIndex > 50) {
            lastSavedIndex = state.currentWordIndex
            viewModelScope.launch {
                state.book?.let {
                    bookRepository.updateProgress(it.id, state.currentWordIndex)
                }
            }
        }
    }

    private fun saveSession() {
        val state = _uiState.value
        val book = state.book ?: return
        val endTime = System.currentTimeMillis()
        val durationMs = endTime - sessionStartTime
        if (durationMs < 5000) return
        val wordsRead = state.currentWordIndex
        val wpm = if (durationMs > 0) (wordsRead / (durationMs / 60000.0)).toInt() else 0
        viewModelScope.launch {
            sessionRepository.insertSession(
                ReadingSession(
                    bookId = book.id,
                    mode = state.currentMode,
                    startTime = sessionStartTime,
                    endTime = endTime,
                    wordsRead = wordsRead,
                    wpm = wpm
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
