package com.alexis.hifiplayer.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.alexis.hifiplayer.domain.model.OutputFormatPreference
import com.alexis.hifiplayer.domain.repository.AudioSettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Single Preferences DataStore instance for the audio settings preference file.
 * Declared once at file scope so every [PreferencesAudioSettingsRepository]
 * shares the same underlying instance.
 */
private val Context.audioSettingsDataStore by preferencesDataStore(name = "audio_settings")

private val HIF_ENABLED_KEY = booleanPreferencesKey("hifi_enabled")
private val PREFERRED_SAMPLE_RATE_KEY = booleanPreferencesKey("preferred_sample_rate_set")
private val PREFERRED_SAMPLE_RATE_VALUE_KEY = intPreferencesKey("preferred_sample_rate_value")
private val PREFERRED_BIT_DEPTH_KEY = booleanPreferencesKey("preferred_bit_depth_set")
private val PREFERRED_BIT_DEPTH_VALUE_KEY = intPreferencesKey("preferred_bit_depth_value")

// Fase 3.1: claves para preferencias de formato de salida
private val PREFER_HIGH_RES_PCM_KEY = booleanPreferencesKey("prefer_high_res_pcm")
private val PREFER_USB_DAC_KEY = booleanPreferencesKey("prefer_usb_dac")
private val ALLOW_OFFLOAD_KEY = booleanPreferencesKey("allow_offload")

/**
 * DataStore Preferences-backed implementation of [AudioSettingsRepository].
 *
 * Persiste preferencias de audio en un archivo DataStore dedicado (`audio_settings`).
 * Cada preferencia se guarda de forma independiente; las ausentes usan valores
 * conservadores por defecto (sin activar funciones que alteren la señal).
 */
class PreferencesAudioSettingsRepository(
    private val context: Context
) : AudioSettingsRepository {

    override suspend fun isHiFiEnabled(): Boolean? =
        context.audioSettingsDataStore.data.first()
            .let { prefs -> prefs[HIF_ENABLED_KEY] }

    override fun observeHiFiEnabled(): Flow<Boolean?> =
        context.audioSettingsDataStore.data
            .catch { throwable ->
                if (throwable is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw throwable
                }
            }
            .map { prefs -> prefs[HIF_ENABLED_KEY] }
            .distinctUntilChanged()

    override suspend fun setHiFiEnabled(enabled: Boolean) {
        context.audioSettingsDataStore.edit { prefs ->
            prefs[HIF_ENABLED_KEY] = enabled
        }
    }

    override suspend fun getPreferredSampleRate(): Int? =
        context.audioSettingsDataStore.data.first()
            .let { prefs ->
                if (prefs[PREFERRED_SAMPLE_RATE_KEY] == true) {
                    prefs[PREFERRED_SAMPLE_RATE_VALUE_KEY]
                } else {
                    null
                }
            }

    override fun observePreferredSampleRate(): Flow<Int?> =
        context.audioSettingsDataStore.data
            .catch { throwable ->
                if (throwable is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw throwable
                }
            }
            .map { prefs ->
                if (prefs[PREFERRED_SAMPLE_RATE_KEY] == true) {
                    prefs[PREFERRED_SAMPLE_RATE_VALUE_KEY]
                } else {
                    null
                }
            }
            .distinctUntilChanged()

    override suspend fun setPreferredSampleRate(sampleRate: Int?) {
        context.audioSettingsDataStore.edit { prefs ->
            if (sampleRate != null) {
                prefs[PREFERRED_SAMPLE_RATE_KEY] = true
                prefs[PREFERRED_SAMPLE_RATE_VALUE_KEY] = sampleRate
            } else {
                prefs[PREFERRED_SAMPLE_RATE_KEY] = false
                prefs.remove(PREFERRED_SAMPLE_RATE_VALUE_KEY)
            }
        }
    }

    override suspend fun getPreferredBitDepth(): Int? =
        context.audioSettingsDataStore.data.first()
            .let { prefs ->
                if (prefs[PREFERRED_BIT_DEPTH_KEY] == true) {
                    prefs[PREFERRED_BIT_DEPTH_VALUE_KEY]
                } else {
                    null
                }
            }

    override fun observePreferredBitDepth(): Flow<Int?> =
        context.audioSettingsDataStore.data
            .catch { throwable ->
                if (throwable is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw throwable
                }
            }
            .map { prefs ->
                if (prefs[PREFERRED_BIT_DEPTH_KEY] == true) {
                    prefs[PREFERRED_BIT_DEPTH_VALUE_KEY]
                } else {
                    null
                }
            }
            .distinctUntilChanged()

    override suspend fun setPreferredBitDepth(bitDepth: Int?) {
        context.audioSettingsDataStore.edit { prefs ->
            if (bitDepth != null) {
                prefs[PREFERRED_BIT_DEPTH_KEY] = true
                prefs[PREFERRED_BIT_DEPTH_VALUE_KEY] = bitDepth
            } else {
                prefs[PREFERRED_BIT_DEPTH_KEY] = false
                prefs.remove(PREFERRED_BIT_DEPTH_VALUE_KEY)
            }
        }
    }

    // --- Fase 3.1: Preferencias de formato de salida ---

    override suspend fun getOutputFormatPreference(): OutputFormatPreference =
        context.audioSettingsDataStore.data.first()
            .let { prefs ->
                OutputFormatPreference(
                    preferHighResPcm = prefs[PREFER_HIGH_RES_PCM_KEY] ?: false,
                    preferUsbDac = prefs[PREFER_USB_DAC_KEY] ?: false,
                    allowOffload = prefs[ALLOW_OFFLOAD_KEY] ?: false
                )
            }

    override fun observeOutputFormatPreference(): Flow<OutputFormatPreference> =
        context.audioSettingsDataStore.data
            .catch { throwable ->
                if (throwable is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw throwable
                }
            }
            .map { prefs ->
                OutputFormatPreference(
                    preferHighResPcm = prefs[PREFER_HIGH_RES_PCM_KEY] ?: false,
                    preferUsbDac = prefs[PREFER_USB_DAC_KEY] ?: false,
                    allowOffload = prefs[ALLOW_OFFLOAD_KEY] ?: false
                )
            }
            .distinctUntilChanged()

    override suspend fun setOutputFormatPreference(preference: OutputFormatPreference) {
        context.audioSettingsDataStore.edit { prefs ->
            prefs[PREFER_HIGH_RES_PCM_KEY] = preference.preferHighResPcm
            prefs[PREFER_USB_DAC_KEY] = preference.preferUsbDac
            prefs[ALLOW_OFFLOAD_KEY] = preference.allowOffload
        }
    }
}