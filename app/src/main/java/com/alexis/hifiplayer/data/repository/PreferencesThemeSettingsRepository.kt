package com.alexis.hifiplayer.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.alexis.hifiplayer.domain.model.ThemeMode
import com.alexis.hifiplayer.domain.repository.ThemeSettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Single Preferences DataStore instance for the theme preference file.
 * Declared once at file scope so every [PreferencesThemeSettingsRepository]
 * shares the same underlying instance.
 */
private val Context.themeSettingsDataStore by preferencesDataStore(name = "theme_settings")

private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")

/**
 * DataStore Preferences-backed implementation of [ThemeSettingsRepository].
 * Maps the stored String to a [ThemeMode], falling back to [ThemeMode.DYNAMIC]
 * when the key is missing or holds an unknown value.
 */
class PreferencesThemeSettingsRepository(
    private val context: Context
) : ThemeSettingsRepository {

    override fun observeThemeMode(): Flow<ThemeMode> =
        context.themeSettingsDataStore.data
            .catch { throwable ->
                if (throwable is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw throwable
                }
            }
            .map { preferences ->
                preferences[THEME_MODE_KEY].toThemeMode()
            }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.themeSettingsDataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }
}

private fun String?.toThemeMode(): ThemeMode =
    ThemeMode.entries.firstOrNull { it.name == this } ?: ThemeMode.DYNAMIC
