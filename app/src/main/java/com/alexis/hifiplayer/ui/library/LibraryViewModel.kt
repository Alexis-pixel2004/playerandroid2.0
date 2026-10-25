package com.alexis.hifiplayer.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alexis.hifiplayer.domain.model.Song
import com.alexis.hifiplayer.domain.repository.LibraryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

data class LibraryUiState(
    val isLoading: Boolean = true,
    val songs: List<Song> = emptyList(),
    val isEmpty: Boolean = false,
    val error: String? = null
)

class LibraryViewModel(
    private val repository: LibraryRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    private var collectionJob: Job? = null
    private var permissionRefreshRequested = false

    init {
        observeSongs()
    }

    private fun observeSongs() {
        reloadInternal()
    }

    private fun reloadInternal() {
        collectionJob?.cancel()
        collectionJob = viewModelScope.launch {
            repository.observeSongs()
                .onStart { _uiState.value = _uiState.value.copy(isLoading = true, error = null) }
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isEmpty = true,
                        error = e.message
                    )
                }
                .collect { list ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        songs = list,
                        isEmpty = list.isEmpty(),
                        error = null
                    )
                }
        }
    }

    fun reload() {
        reloadInternal()
    }

    /**
     * Recarga la biblioteca la primera vez que el permiso de audio está disponible.
     * Idempotente: reentrar en la pestaña (recomposición) no vuelve a suscribirse a MediaStore.
     * El botón Reintentar sigue usando [reload] de forma explícita.
     */
    fun onAudioPermissionAvailable() {
        if (permissionRefreshRequested) return
        permissionRefreshRequested = true
        reload()
    }

    override fun onCleared() {
        super.onCleared()
        collectionJob?.cancel()
    }
}

/**
 * Factoría tipada para construir [LibraryViewModel] con su repositorio.
 * Se usa desde MainActivity con un ViewModelStoreOwner ligado a la actividad,
 * de modo que el ViewModel y su colección de MediaStore sobrevivan a la rotación
 * y no se recree al cambiar de pestaña.
 */
class LibraryViewModelFactory(
    private val repository: LibraryRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
            return LibraryViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
