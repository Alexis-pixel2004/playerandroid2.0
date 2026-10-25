package com.alexis.hifiplayer.ui.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.alexis.hifiplayer.domain.model.Song
import com.alexis.hifiplayer.domain.playback.PlaybackController
import com.alexis.hifiplayer.domain.playback.PlaybackState
import kotlinx.coroutines.flow.StateFlow

/**
 * Fachada de reproducción para la UI.
 *
 * No mantiene estado propio ni una segunda copia de la cola: expone directamente
 * el [PlaybackState] del [PlaybackController], que es la única fuente de verdad.
 * Por eso no crea ninguna suscripción propia que cancelar; la recolección la hace
 * la UI según su ciclo de vida.
 *
 * No depende de ExoPlayer ni de ninguna clase de Media3: solo del contrato de
 * dominio [PlaybackController].
 */
class PlaybackViewModel(
    private val controller: PlaybackController
) : ViewModel() {

    /** Estado de reproducción observable por la UI. */
    val state: StateFlow<PlaybackState> = controller.state

    fun setQueue(songs: List<Song>, startIndex: Int = 0) =
        controller.setQueue(songs, startIndex)

    fun play() = controller.play()

    fun pause() = controller.pause()

    fun togglePlayPause() = controller.togglePlayPause()

    fun next() = controller.next()

    fun previous() = controller.previous()

    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)

    // Este ViewModel es el propietario del controlador (ámbito de actividad): libera
    // el motor una sola vez al final de su ciclo de vida, no en cada rotación.
    override fun onCleared() {
        super.onCleared()
        controller.release()
    }
}

/**
 * Factoría tipada para construir [PlaybackViewModel].
 *
 * Recibe un proveedor `() -> PlaybackController` en lugar de un controlador ya
 * construido: el controlador (y por tanto ExoPlayer) se crea únicamente dentro de
 * [create], cuando el `ViewModelProvider` decide construir el ViewModel. Así no se
 * instancia un motor nuevo en cada rotación (el productor de `by viewModels` sí se
 * invoca en cada recreación, pero [create] no).
 *
 * Sigue el mismo patrón que `LibraryViewModelFactory`.
 */
class PlaybackViewModelFactory(
    private val controllerProvider: () -> PlaybackController
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlaybackViewModel::class.java)) {
            return PlaybackViewModel(controllerProvider()) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
    }
}
