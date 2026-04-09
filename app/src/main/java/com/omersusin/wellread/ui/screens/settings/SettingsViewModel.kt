package com.omersusin.wellread.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omersusin.wellread.domain.model.ReadingMode
import com.omersusin.wellread.domain.model.UserPreferences
import com.omersusin.wellread.utils.DataStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = dataStoreManager.userPreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    fun setDarkMode(value: Boolean) = viewModelScope.launch { dataStoreManager.setDarkMode(value) }
    fun setDynamicColor(value: Boolean) = viewModelScope.launch { dataStoreManager.setDynamicColor(value) }
    fun setDefaultWpm(value: Int) = viewModelScope.launch { dataStoreManager.setDefaultWpm(value) }
    fun setDefaultMode(value: ReadingMode) = viewModelScope.launch { dataStoreManager.setDefaultMode(value) }
    fun setFontSize(value: Float) = viewModelScope.launch { dataStoreManager.setFontSize(value) }
    fun setDailyGoal(value: Int) = viewModelScope.launch { dataStoreManager.setDailyGoal(value) }
    fun setBionicStrength(value: Float) = viewModelScope.launch { dataStoreManager.setBionicStrength(value) }
    fun setFlashChunkSize(value: Int) = viewModelScope.launch { dataStoreManager.setFlashChunkSize(value) }
}
