package com.alexis.hifiplayer.domain.repository

import com.alexis.hifiplayer.domain.model.OutputFormatPreference
import kotlinx.coroutines.flow.Flow

/**
 * Domain contract for audio-related user preferences.
 * Implementation can be backed by DataStore, SharedPreferences or other sources.
 * This contract is Android-agnostic.
 */
interface AudioSettingsRepository {

    /**
     * True if Hi-Fi/bit-perfect preference is enabled, null if unknown.
     */
    suspend fun isHiFiEnabled(): Boolean?

    /**
     * Observes Hi-Fi preference changes.
     */
    fun observeHiFiEnabled(): Flow<Boolean?>

    /**
     * Persists Hi-Fi preference.
     */
    suspend fun setHiFiEnabled(enabled: Boolean)

    /**
     * Preferred sample rate hint (Hz). Null means automatic.
     */
    suspend fun getPreferredSampleRate(): Int?

    /**
     * Observes preferred sample rate changes.
     */
    fun observePreferredSampleRate(): Flow<Int?>

    /**
     * Persists preferred sample rate hint.
     */
    suspend fun setPreferredSampleRate(sampleRate: Int?)

    /**
     * Preferred bit depth hint (bits). Null means automatic.
     */
    suspend fun getPreferredBitDepth(): Int?

    /**
     * Observes preferred bit depth changes.
     */
    fun observePreferredBitDepth(): Flow<Int?>

    /**
     * Persists preferred bit depth hint.
     */
    suspend fun setPreferredBitDepth(bitDepth: Int?)

    // --- Fase 3.1: Preferencias de formato de salida ---

    /**
     * Current output format preferences.
     */
    suspend fun getOutputFormatPreference(): OutputFormatPreference

    /**
     * Observes output format preference changes.
     */
    fun observeOutputFormatPreference(): Flow<OutputFormatPreference>

    /**
     * Persists output format preferences.
     */
    suspend fun setOutputFormatPreference(preference: OutputFormatPreference)
}
