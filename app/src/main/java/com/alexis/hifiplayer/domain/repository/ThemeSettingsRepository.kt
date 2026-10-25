package com.alexis.hifiplayer.domain.repository

import com.alexis.hifiplayer.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * Domain contract for the visual color scheme preference.
 * Implementation can be backed by DataStore, SharedPreferences or other sources.
 * This contract is Android-agnostic.
 */
interface ThemeSettingsRepository {

    /**
     * Observes the selected theme mode.
     */
    fun observeThemeMode(): Flow<ThemeMode>

    /**
     * Persists the selected theme mode.
     */
    suspend fun setThemeMode(mode: ThemeMode)
}
