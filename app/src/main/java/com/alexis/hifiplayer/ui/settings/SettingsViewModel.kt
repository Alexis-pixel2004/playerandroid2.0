package com.alexis.hifiplayer.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alexis.hifiplayer.domain.model.ThemeMode
import com.alexis.hifiplayer.domain.repository.ThemeSettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel de la preferencia visual del tema.
 *
 * Expone el [ThemeMode] persistido como [StateFlow]. La preferencia guardada por
 * el repositorio es la única fuente de verdad: este ViewModel no mantiene una copia
 * propia del modo seleccionado, de modo que una escritura fallida no deja la interfaz
 * mostrando una selección que nunca se guardó.
 */
class SettingsViewModel(
    private val repository: ThemeSettingsRepository
) : ViewModel() {

    /**
     * Modo de tema observable por la interfaz. Vale [ThemeMode.DYNAMIC] hasta que se
     * completa la primera lectura del repositorio.
     */
    val themeMode: StateFlow<ThemeMode> = repository.observeThemeMode()
        .stateIn(
            scope = viewModelScope,
            // Mantiene la colección viva durante un cambio de configuración (rotación)
            // y la detiene cuando la UI deja de observar: una sola colección, sin duplicados.
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ThemeMode.DYNAMIC
        )

    /**
     * Persiste el [mode] seleccionado. No se actualiza el estado de forma optimista:
     * la interfaz reflejará el modo cuando el repositorio emita el valor guardado.
     */
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            try {
                repository.setThemeMode(mode)
            } catch (e: IOException) {
                // Fallo esperado de escritura en disco: se registra y se conserva el valor
                // persistido como fuente de verdad. Los errores inesperados no se ocultan.
                Log.e(TAG, "No se pudo persistir el modo de tema", e)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val TAG = "SettingsViewModel"
    }
}

/**
 * Factoría tipada para construir [SettingsViewModel] con su repositorio.
 * Sigue el mismo patrón que `LibraryViewModelFactory`.
 */
class SettingsViewModelFactory(
    private val repository: ThemeSettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
