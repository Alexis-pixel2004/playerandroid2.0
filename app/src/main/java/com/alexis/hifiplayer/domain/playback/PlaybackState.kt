package com.alexis.hifiplayer.domain.playback

import com.alexis.hifiplayer.domain.model.AudioTrackConfig
import com.alexis.hifiplayer.domain.model.DecoderInputFormat
import com.alexis.hifiplayer.domain.model.SelectedTrackFormat
import com.alexis.hifiplayer.domain.model.Song

/**
 * Estado técnico del motor respecto a la pista actual.
 *
 * Es deliberadamente independiente de [PlaybackState.isPlaying]: [READY] indica que
 * la pista está preparada y puede estar sonando o en pausa, mientras que [BUFFERING]
 * indica que todavía se está cargando. No implica ninguna decisión sobre el formato
 * efectivo ni sobre bit-perfect.
 */
enum class PlaybackStatus {
    /** Sin pista preparada (cola vacía o reproducción detenida). */
    IDLE,
    /** La pista actual se está cargando/preparando. */
    BUFFERING,
    /** La pista actual está preparada (sonando o en pausa). */
    READY,
    /** La pista actual terminó de reproducirse. */
    ENDED
}

/**
 * Error de reproducción a nivel de dominio, desacoplado de las excepciones del motor.
 */
data class PlaybackError(
    val message: String,
    val cause: PlaybackErrorCause = PlaybackErrorCause.UNKNOWN
)

/**
 * Causa conservadora de un [PlaybackError]. Evita mapear errores técnicos del motor
 * a categorías de dominio que no se pueden determinar.
 */
enum class PlaybackErrorCause {
    UNKNOWN,
    SOURCE_NOT_FOUND,
    UNSUPPORTED_FORMAT,
    DECODING_ERROR,
    IO_ERROR
}

/**
 * Estado de la sesión de reproducción.
 *
 * Es la única fuente de verdad de la cola y de la pista actual: [currentSong] se
 * deriva de [queue] y [currentIndex], sin guardar una segunda copia de la canción.
 *
 * Los valores por defecto representan el estado "sin reproducción": cola vacía,
 * sin pista actual, motor en [PlaybackStatus.IDLE], pausado y sin error. Ningún
 * campo se activa sin que el motor lo confirme (no se simula progreso).
 */
data class PlaybackState(
    /** Cola de reproducción. Vacía por defecto; una cola vacía es un estado válido. */
    val queue: List<Song> = emptyList(),
    /** Índice de la pista actual en [queue]. `-1` cuando no hay pista seleccionada. */
    val currentIndex: Int = -1,
    /** Estado de preparación/carga del motor, independiente de [isPlaying]. */
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    /** `true` solo cuando el motor confirma que hay reproducción activa. */
    val isPlaying: Boolean = false,
    /** Posición de reproducción actual en milisegundos (`0` cuando no aplica). */
    val positionMs: Long = 0L,
    /**
     * Duración real de la pista actual en milisegundos, o `null` si aún se desconoce.
     * `null` (desconocida) es distinto de `0L` (duración conocida igual a cero).
     */
    val durationMs: Long? = null,
    /** Posición hasta la que hay datos disponibles, o `null` si se desconoce. */
    val bufferedPositionMs: Long? = null,
    /**
     * Formato de la pista que el motor **selecciona** para la pista actual, o `null` si
     * no hay selección determinable (cola vacía, aún sin pistas, o sesión perdida).
     *
     * Describe el stream declarado por el contenedor, no el PCM decodificado ni el
     * formato efectivo recibido por el DAC. No implica bit-perfect.
     */
    val selectedTrackFormat: SelectedTrackFormat? = null,
    /**
     * Formato de entrada al decodificador observado por ExoPlayer
     * (`AnalyticsListener.onAudioInputFormatChanged`), o `null` si no disponible.
     *
     * Describe el stream tal como llega al decodificador (comprimido o PCM).
     * No es el formato del AudioTrack ni el efectivo del DAC.
     */
    val decoderInputFormat: DecoderInputFormat? = null,
    /**
     * Configuración real del `AudioTrack` creada por ExoPlayer
     * (`AnalyticsListener.onAudioTrackInitialized` → `AudioSink.AudioTrackConfig`),
     * o `null` si no disponible.
     *
     * Es lo que ExoPlayer configura en el AudioTrack; el mixer del sistema puede
     * alterarlo (resampleo, etc.). **No** es el formato efectivo del DAC.
     */
    val audioTrackConfig: AudioTrackConfig? = null,
    /** Error de la pista actual, o `null` si no hay error. */
    val error: PlaybackError? = null
) {
    /** Canción actual, derivada de [queue] y [currentIndex]. No se almacena aparte. */
    val currentSong: Song?
        get() = queue.getOrNull(currentIndex)

    /** `true` si la cola contiene al menos una canción. */
    val hasQueue: Boolean
        get() = queue.isNotEmpty()

    /**
     * `true` si existe una canción siguiente.
     * Asume el comportamiento sin repetición (aún no implementada).
     */
    val hasNext: Boolean
        get() = currentIndex >= 0 && currentIndex < queue.lastIndex

    /**
     * `true` si existe una canción anterior.
     * Asume el comportamiento sin repetición (aún no implementada).
     */
    val hasPrevious: Boolean
        get() = currentIndex > 0
}
