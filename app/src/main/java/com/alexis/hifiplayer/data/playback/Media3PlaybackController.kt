package com.alexis.hifiplayer.data.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.alexis.hifiplayer.domain.model.Song
import com.alexis.hifiplayer.domain.playback.PlaybackController
import com.alexis.hifiplayer.domain.playback.PlaybackError
import com.alexis.hifiplayer.domain.playback.PlaybackErrorCause
import com.alexis.hifiplayer.domain.playback.PlaybackState
import com.alexis.hifiplayer.domain.playback.PlaybackStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Intervalo de refresco del progreso durante la reproducción (ms). */
private const val ProgressUpdateIntervalMs = 500L

/**
 * Implementación de [PlaybackController] sobre AndroidX Media3 ExoPlayer.
 *
 * **Implementación antigua que permanece en el proyecto como referencia histórica.**
 * Crea y posee su propio [ExoPlayer], por lo que **no está instanciada** en ninguna
 * parte y **no forma parte de la ruta de reproducción vigente**. La ruta activa usa
 * [PlaybackService] (único propietario del [ExoPlayer]) a través de
 * `MediaSessionPlaybackController`; no debe usarse junto al servicio para no crear un
 * segundo reproductor.
 *
 * Traduce el estado real del [Player] a [PlaybackState] y mantiene a ExoPlayer como
 * única fuente de verdad de la cola y del índice actual (no se duplica la cola como
 * estado mutable propio).
 *
 * Confinamiento de hilo: ExoPlayer se crea y se usa desde el hilo que lo instancia
 * (por defecto, el looper principal de la aplicación). Crear, invocar comandos y
 * [release] siempre desde ese mismo hilo.
 *
 * Progreso: mientras el [Player] reproduce de verdad, una única corrutina refresca
 * [state] cada [ProgressUpdateIntervalMs] reutilizando `syncState()`. Se detiene al
 * pausar/terminar o liberar; nunca hay más de una tarea activa.
 *
 * Audio focus y dispositivos de salida: se delegan en ExoPlayer (ver constructor);
 * no se añade un `AudioFocusRequest` manual ni se usa [com.alexis.hifiplayer.audio.AudioEngine].
 *
 * La reproducción real vía ExoPlayer **no** demuestra bit-perfect: esta clase no
 * hace ninguna afirmación sobre el formato efectivo de la salida.
 */
class Media3PlaybackController(context: Context) : PlaybackController {

    private val mediaAudioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    private val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        // handleAudioFocus = true: ExoPlayer solicita y gestiona el audio focus.
        .setAudioAttributes(mediaAudioAttributes, /* handleAudioFocus = */ true)
        // Pausa automáticamente al desconectar auriculares/salida de audio.
        .setHandleAudioBecomingNoisy(true)
        .build()

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    // Única tarea de refresco periódico de la posición. El scope corre en el looper
    // principal (mismo hilo con el que se crea y usa el Player). Se cancela en
    // release() para no dejar trabajo vivo.
    private val progressScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressJob: Job? = null

    /** Último error de reproducción; persiste hasta que cambia de pista o se reanuda. */
    private var lastError: PlaybackError? = null

    /** Evita usar un ExoPlayer ya liberado. */
    private var released: Boolean = false

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            syncState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            lastError = null
            syncState()
        }

        override fun onPlayerError(error: PlaybackException) {
            lastError = error.toDomainError()
            syncState()
        }
    }

    init {
        player.addListener(listener)
    }

    override fun setQueue(songs: List<Song>, startIndex: Int) {
        if (released) return
        lastError = null

        if (songs.isEmpty()) {
            _state.value = PlaybackState()
            player.clearMediaItems()
            return
        }

        val index = startIndex.coerceIn(0, songs.lastIndex)
        // Se publica la cola antes de tocar el Player para que los eventos del
        // listener (buffering/ready) ya encuentren un estado coherente.
        _state.value = PlaybackState(
            queue = songs,
            currentIndex = index,
            status = PlaybackStatus.IDLE,
            isPlaying = false
        )

        player.setMediaItems(songs.map { it.toMediaItem() }, index, 0L)
        // prepare() carga la pista pero no inicia la reproducción (playWhenReady = false).
        player.prepare()
        syncState()
    }

    override fun play() {
        if (released) return
        if (player.mediaItemCount == 0) return
        lastError = null
        if (player.playbackState == Player.STATE_IDLE) {
            // Permite reintentar tras un error o una detención previa.
            player.prepare()
        }
        player.play()
        syncState()
    }

    override fun pause() {
        if (released) return
        player.pause()
        syncState()
    }

    override fun togglePlayPause() {
        if (released) return
        if (player.mediaItemCount == 0) return
        if (player.playbackState == Player.STATE_ENDED) {
            // La pista terminó y playWhenReady puede seguir en true: alternar solo
            // por playWhenReady pausaría en vez de reiniciar. Se vuelve al inicio de
            // la canción actual y se reproduce.
            player.seekTo(0L)
            play()
            return
        }
        if (player.playWhenReady) pause() else play()
    }

    override fun next() {
        if (released) return
        if (!player.hasNextMediaItem()) return
        player.seekToNextMediaItem()
        syncState()
    }

    override fun previous() {
        if (released) return
        if (!player.hasPreviousMediaItem()) return
        player.seekToPreviousMediaItem()
        syncState()
    }

    override fun seekTo(positionMs: Long) {
        if (released) return
        if (player.mediaItemCount == 0) return
        val duration = player.duration
        val target = positionMs.coerceAtLeast(0L)
        val clamped = if (duration != C.TIME_UNSET && duration > 0L) {
            target.coerceAtMost(duration)
        } else {
            target
        }
        player.seekTo(clamped)
        syncState()
    }

    /**
     * Libera ExoPlayer. Idempotente. Debe invocarse desde el mismo hilo que creó el
     * controlador. Tras [release], todos los comandos son no-op.
     *
     * Propiedad/ciclo de vida (a decidir en el cableado): el controlador debe
     * sobrevivir a la rotación de la actividad, por lo que su dueño previsto es un
     * `ViewModel` de ámbito de actividad (o el futuro `MediaSessionService`), que
     * llamará a [release] en su `onCleared()`/destrucción.
     */
    override fun release() {
        if (released) return
        released = true
        // Detiene y descarta la tarea de refresco y su scope antes de liberar el Player.
        progressJob?.cancel()
        progressJob = null
        progressScope.cancel()
        player.removeListener(listener)
        player.release()
        _state.value = PlaybackState()
    }

    /** Vuelca el estado real del Player a [PlaybackState] sin inventar progreso. */
    private fun syncState() {
        if (released) return
        val current = _state.value
        val hasItems = player.mediaItemCount > 0
        val index = if (hasItems) player.currentMediaItemIndex else -1
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it >= 0L }
        val buffered = player.bufferedPosition.takeIf { it != C.TIME_UNSET && it >= 0L }
        val position = if (hasItems) player.currentPosition.coerceAtLeast(0L) else 0L

        _state.value = current.copy(
            currentIndex = index,
            status = player.playbackState.toPlaybackStatus(),
            isPlaying = player.isPlaying,
            positionMs = position,
            durationMs = duration,
            bufferedPositionMs = buffered,
            error = lastError
        )
        // Reconciliación idempotente del refresco con el estado real de reproducción.
        updateProgressUpdates()
    }

    /**
     * Arranca o detiene el refresco periódico según haya reproducción activa.
     *
     * Idempotente: si ya hay una [Job] viva no crea otra; si no hay reproducción,
     * la cancela. `progressScope` usa `Dispatchers.Main` (no `immediate`) para que
     * el cuerpo de la corrutina no se ejecute antes de asignar `progressJob`, lo que
     * podría lanzar una segunda tarea desde el propio `syncState()`.
     */
    private fun updateProgressUpdates() {
        if (released || !player.isPlaying) {
            progressJob?.cancel()
            progressJob = null
            return
        }
        if (progressJob?.isActive == true) return
        progressJob = progressScope.launch {
            while (isActive) {
                syncState()
                delay(ProgressUpdateIntervalMs)
            }
        }
    }
}

/** Traduce un [Song] a [MediaItem] conservando su URI y usando el id como mediaId. */
private fun Song.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .build()
    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(contentUri)
        .setMediaMetadata(metadata)
        .build()
}

private fun Int.toPlaybackStatus(): PlaybackStatus = when (this) {
    Player.STATE_BUFFERING -> PlaybackStatus.BUFFERING
    Player.STATE_READY -> PlaybackStatus.READY
    Player.STATE_ENDED -> PlaybackStatus.ENDED
    else -> PlaybackStatus.IDLE
}

/**
 * Mapea errores de Media3 a [PlaybackError] de dominio. Al ser reproducción local
 * (content URIs), el foco está en errores de fichero/permisos y de parser/decoder;
 * cualquier caso no contemplado cae en [PlaybackErrorCause.UNKNOWN] en vez de
 * inventar una categoría.
 */
private fun PlaybackException.toDomainError(): PlaybackError {
    val cause = when (errorCode) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> PlaybackErrorCause.SOURCE_NOT_FOUND

        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> PlaybackErrorCause.UNSUPPORTED_FORMAT

        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> PlaybackErrorCause.DECODING_ERROR

        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> PlaybackErrorCause.IO_ERROR

        else -> PlaybackErrorCause.UNKNOWN
    }
    return PlaybackError(message = message ?: errorCodeName, cause = cause)
}
