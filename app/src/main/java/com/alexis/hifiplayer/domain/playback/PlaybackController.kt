package com.alexis.hifiplayer.domain.playback

import com.alexis.hifiplayer.domain.model.Song
import kotlinx.coroutines.flow.StateFlow

/**
 * Contrato del motor de reproducción, independiente de la tecnología concreta.
 * La implementación prevista (fase posterior) es AndroidX Media3 ExoPlayer, pero
 * esta interfaz no importa ni menciona Media3.
 *
 * [state] es la única fuente de verdad de la cola y de la pista actual: los
 * llamantes no deben mantener su propia copia.
 *
 * Reglas generales:
 * - Con la cola vacía, las operaciones de reproducción son no-op.
 * - [setQueue] no inicia la reproducción por sí solo.
 * - Las operaciones son idempotentes cuando ya se está en el estado pedido.
 */
interface PlaybackController {

    /** Estado observable del reproductor. Única fuente de verdad. */
    val state: StateFlow<PlaybackState>

    /**
     * Establece la cola y selecciona la pista inicial.
     *
     * Precondición: si [songs] no está vacío, [startIndex] debe pertenecer a
     * `0..songs.lastIndex`. Si [songs] está vacío, la cola queda vacía y no hay
     * pista actual, con independencia de [startIndex].
     *
     * No inicia la reproducción: la pista seleccionada queda sin reproducir.
     */
    fun setQueue(songs: List<Song>, startIndex: Int = 0)

    /** Inicia o reanuda la reproducción de la pista actual. No-op con cola vacía. */
    fun play()

    /** Pausa la reproducción. No-op si no se está reproduciendo. */
    fun pause()

    /** Alterna entre reproducir y pausar. No-op con cola vacía. */
    fun togglePlayPause()

    /**
     * Salta a la pista siguiente.
     * No-op si no hay pista siguiente (sin repetición).
     */
    fun next()

    /**
     * Salta a la pista anterior.
     * No-op si no hay pista anterior.
     */
    fun previous()

    /**
     * Busca una posición en la pista actual.
     *
     * La implementación real debe acotar [positionMs] al rango válido
     * (`0..duration` cuando la duración se conoce). No-op con cola vacía.
     */
    fun seekTo(positionMs: Long)

    /**
     * Libera los recursos asociados al motor de reproducción.
     *
     * Precondición: debe invocarlo el propietario del controlador (por ejemplo, el
     * `ViewModel` que lo creó) y una sola vez, al final de su ciclo de vida. Debe ser
     * idempotente: llamadas repetidas no deben fallar.
     *
     * Tras [release], el estado vuelve al valor por defecto (cola vacía) y los demás
     * comandos son no-op. Es la única operación de ciclo de vida del contrato; el
     * resto son comandos de reproducción.
     */
    fun release()
}

