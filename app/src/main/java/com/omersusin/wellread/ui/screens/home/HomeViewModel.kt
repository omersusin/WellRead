package com.omersusin.wellread.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omersusin.wellread.data.repository.BookRepository
import com.omersusin.wellread.data.repository.SessionRepository
import com.omersusin.wellread.domain.model.Book
import com.omersusin.wellread.domain.model.ReadingStats
import com.omersusin.wellread.domain.model.UserPreferences
import com.omersusin.wellread.utils.DataStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val recentBooks: List<Book> = emptyList(),
    val stats: ReadingStats = ReadingStats(),
    val preferences: UserPreferences = UserPreferences(),
    val isLoading: Boolean = true,
    val greeting: String = "Good morning"
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val sessionRepository: SessionRepository,
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                bookRepository.getRecentBooks(),
                dataStoreManager.userPreferences
            ) { books, prefs ->
                books to prefs
            }.collect { (books, prefs) ->
                val stats = sessionRepository.getStats()
                _uiState.update {
                    it.copy(
                        recentBooks = books,
                        stats = stats,
                        preferences = prefs,
                        isLoading = false,
                        greeting = getGreeting()
                    )
                }
            }
        }
    }

    private fun getGreeting(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            in 18..21 -> "Good evening"
            else -> "Good night"
        }
    }

    fun refresh() = loadData()
}
