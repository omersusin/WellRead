package com.omersusin.wellread.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.omersusin.wellread.domain.model.ReadingMode
import com.omersusin.wellread.domain.model.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wellread_prefs")

@Singleton
class DataStoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val DEFAULT_WPM = intPreferencesKey("default_wpm")
        val DEFAULT_MODE = stringPreferencesKey("default_mode")
        val FONT_SIZE = floatPreferencesKey("font_size")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val DAILY_GOAL = intPreferencesKey("daily_goal")
        val HAPTIC = booleanPreferencesKey("haptic")
        val BIONIC_STRENGTH = floatPreferencesKey("bionic_strength")
        val FLASH_CHUNK = intPreferencesKey("flash_chunk")
    }

    val userPreferences: Flow<UserPreferences> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            UserPreferences(
                isDarkMode = prefs[Keys.DARK_MODE] ?: true,
                useDynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
                defaultWpm = prefs[Keys.DEFAULT_WPM] ?: 300,
                defaultMode = ReadingMode.valueOf(prefs[Keys.DEFAULT_MODE] ?: ReadingMode.BIONIC.name),
                fontSize = prefs[Keys.FONT_SIZE] ?: 16f,
                fontFamily = prefs[Keys.FONT_FAMILY] ?: "Inter",
                dailyGoalMinutes = prefs[Keys.DAILY_GOAL] ?: 20,
                hapticFeedback = prefs[Keys.HAPTIC] ?: true,
                bionicFixationStrength = prefs[Keys.BIONIC_STRENGTH] ?: 0.5f,
                flashChunkSize = prefs[Keys.FLASH_CHUNK] ?: 1
            )
        }

    suspend fun updatePreferences(update: suspend (MutablePreferences) -> Unit) {
        context.dataStore.edit { update(it) }
    }

    suspend fun setDarkMode(value: Boolean) = updatePreferences { it[Keys.DARK_MODE] = value }
    suspend fun setDynamicColor(value: Boolean) = updatePreferences { it[Keys.DYNAMIC_COLOR] = value }
    suspend fun setDefaultWpm(value: Int) = updatePreferences { it[Keys.DEFAULT_WPM] = value }
    suspend fun setDefaultMode(value: ReadingMode) = updatePreferences { it[Keys.DEFAULT_MODE] = value.name }
    suspend fun setFontSize(value: Float) = updatePreferences { it[Keys.FONT_SIZE] = value }
    suspend fun setDailyGoal(value: Int) = updatePreferences { it[Keys.DAILY_GOAL] = value }
    suspend fun setBionicStrength(value: Float) = updatePreferences { it[Keys.BIONIC_STRENGTH] = value }
    suspend fun setFlashChunkSize(value: Int) = updatePreferences { it[Keys.FLASH_CHUNK] = value }
}
