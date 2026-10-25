package com.alexis.hifiplayer.data.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.MediaController.Listener
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.alexis.hifiplayer.domain.model.AudioTrackConfig
import com.alexis.hifiplayer.domain.model.DecoderInputFormat
import com.alexis.hifiplayer.domain.model.Song
import com.alexis.hifiplayer.domain.playback.PlaybackController
import com.alexis.hifiplayer.domain.playback.PlaybackError
import com.alexis.hifiplayer.domain.playback.PlaybackErrorCause
import com.alexis.hifiplayer.domain.playback.PlaybackState
import com.alexis.hifiplayer.domain.playback.PlaybackStatus
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.alexis.hifiplayer.data.playback.toAudioTrackConfig
import com.alexis.hifiplayer.data.playback.toDecoderInputFormat
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
import java.util.concurrent.Future

/** Intervalo de refresco del progreso durante la reproducción (ms). */
private const val ProgressUpdateIntervalMs = 500L

/** Retardo del único reintento automático tras un fallo de conexión inicial (ms). */
private const val InitialConnectionRetryDelayMs = 1000L

/**
 * Máximo de comandos admitidos durante una recuperación. La secuencia debe estar
 * anclada en `SetQueue`; este tope evita acumular comandos sin límite.
 */
private const val MaxRecoveryCommands = 8

/**
 * Implementación de [PlaybackController] como **cliente** de la `MediaSession` del
 * [PlaybackService], usando `MediaController` de Media3.
 *
 * No crea ni construye un `ExoPlayer`: el único motor vive en el servicio. Esta clase
 * solo conecta con la sesión, traduce el estado del reproductor remoto a
 * [PlaybackState] y reenvía los comandos. Liberar el cliente (`release`) **no**
 * libera el `ExoPlayer` del servicio.
 *
 * ### Conexión asíncrona y recuperación
 * `MediaController.Builder.buildAsync()` conecta en segundo plano. Para no perder
 * comandos recibidos antes de estar conectados, se encolan en [pendingCommands] y se
 * aplican **en orden** en cuanto llega la conexión (secuencia inicial validada
 * `SetQueue` → `Play`). Un fallo de conexión inicial dispara **como máximo un**
 * reintento automático diferido; si vuelve a fallar, no se reintenta solo. Tras una
 * desconexión, la reconexión es **bajo demanda** (al llegar un comando) y solo se
 * admite una secuencia anclada en `SetQueue`; el resto se descarta explícitamente.
 * La cola y la posición **no** sobreviven a la muerte del proceso: no hay persistencia.
 *
 * ### Fuente de verdad de la cola
 * El reproductor es la fuente de verdad. [projectedQueue] es una **proyección** de la
 * `timeline` del reproductor, refrescada solo cuando cambia la `timeline`. Tras
 * `setQueue`, [optimisticQueue]/[optimisticIndex] publican de forma optimista la orden
 * exacta que se acaba de enviar y se descartan en cuanto el reproductor confirma la
 * `timeline`. No hay una cola paralela editable que pueda divergir.
 *
 * Confinamiento de hilo: `MediaController` se crea y se usa desde el hilo principal
 * (looper de la aplicación). Todos los métodos de esta clase deben invocarse desde
 * ese hilo.
 *
 * La reproducción real vía Media3 **no** demuestra bit-perfect: esta clase no hace
 * ninguna afirmación sobre el formato efectivo de salida.
 */
class MediaSessionPlaybackController(context: Context) : PlaybackController {

    private val appContext = context.applicationContext

    /** Token de la sesión del servicio; reutilizable para reconectar. */
    private val sessionToken = SessionToken(
        appContext,
        ComponentName(appContext, PlaybackService::class.java)
    )

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var connectionFuture: Future<MediaController>? = null
    private var released: Boolean = false

    /**
     * `true` cuando la conexión no está disponible (fallo inicial o desconexión
     * inesperada). [dispatch] usa este estado para decidir cuándo intentar una
     * reconexión bajo demanda.
     */
    private var connectionLost: Boolean = false

    /** `true` una vez establecida al menos una conexión con la sesión. */
    private var initialConnectionEstablished: Boolean = false

    /** `true` cuando ya se consumió el único reintento automático de conexión inicial. */
    private var autoRetryUsed: Boolean = false

    /** Reintento automático diferido pendiente; a lo sumo uno. */
    private var retryJob: Job? = null

    /**
     * Comandos recibidos sin conexión. Durante la conexión inicial se aplican en orden
     * (FIFO); durante una recuperación solo puede contener una secuencia anclada en
     * `SetQueue`.
     */
    private val pendingCommands = ArrayDeque<Command>()

    /** Último error de reproducción; persiste hasta que cambia de pista o se reanuda. */
    private var lastError: PlaybackError? = null

    /** Proyección de la cola derivada de la `timeline` del reproductor. */
    private var projectedQueue: List<Song> = emptyList()

    /** Cola del último `setQueue`, publicada de forma optimista hasta confirmarse. */
    private var optimisticQueue: List<Song>? = null
    private var optimisticIndex: Int = -1

    private val progressScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            syncState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncState()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            // El reproductor ya refleja los items: descarta la proyección optimista
            // y reconcilia la cola con la timeline real (fuente de verdad).
            optimisticQueue = null
            optimisticIndex = -1
            refreshProjectedQueue()
            syncState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            lastError = null
            syncState()
        }

        override fun onTracksChanged(tracks: Tracks) {
            // La selección de pistas cambió (nueva pista, cambio de selección…): se
            // refresca el estado para actualizar el formato seleccionado sin esperar al
            // refresco periódico de posición.
            syncState()
        }

        override fun onPlayerError(exception: PlaybackException) {
            lastError = exception.toDomainError()
            syncState()
        }
    }

    private val controllerListener = object : Listener {
        override fun onDisconnected(controller: MediaController) {
            // `onDisconnected` también se invoca tras `release()`: un cierre
            // intencional no es un fallo y `release()` ya limpió todo.
            if (released) return
            // Solo la desconexión del controlador vigente invalida el estado. Un
            // callback tardío de un controlador antiguo no debe alterar una conexión
            // más reciente.
            if (this@MediaSessionPlaybackController.controller !== controller) return

            this@MediaSessionPlaybackController.controller = null
            connectionLost = true
            pendingCommands.clear()
            optimisticQueue = null
            optimisticIndex = -1
            projectedQueue = emptyList()
            stopProgressUpdates()
            _state.value = PlaybackState()
            // No se reconecta automáticamente: la recuperación es bajo demanda
            // (`ensureConnectingOnDemand`), al recibir un comando válido.
        }

        override fun onCustomCommand(
            controller: MediaController,
            command: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            // Comandos personalizados desde el servicio (Etapa 2b): actualizan
            // decoderInputFormat y audioTrackConfig en PlaybackState.
            when (command.customAction) {
                "com.alexis.hifiplayer.COMMAND_DECODER_INPUT_FORMAT" -> {
                    val format = args.toDecoderInputFormat()
                    _state.value = _state.value.copy(decoderInputFormat = format)
                }
                "com.alexis.hifiplayer.COMMAND_AUDIO_TRACK_CONFIG" -> {
                    val config = args.toAudioTrackConfig()
                    _state.value = _state.value.copy(audioTrackConfig = config)
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, Bundle()))
        }
    }

    init {
        connect()
    }

    /**
     * Inicia una conexión con la sesión del servicio. Protección *single-flight*: no
     * hay más de una conexión pendiente. El futuro se conserva hasta que termina
     * (requisito de `buildAsync`) y se limpia en [onConnectionFutureDone].
     */
    private fun connect() {
        if (released || connectionFuture != null) return
        val future = MediaController.Builder(appContext, sessionToken)
            .setListener(controllerListener)
            .buildAsync()
        connectionFuture = future
        future.addListener(
            { onConnectionFutureDone(future) },
            ContextCompat.getMainExecutor(appContext)
        )
    }

    /**
     * El futuro de conexión terminó (éxito o fallo). Solo se procesa el futuro
     * vigente: un callback tardío de una conexión antigua no debe alterar una conexión
     * más reciente. El futuro ya está completo, así que limpiarlo aquí no lo libera
     * prematuramente.
     */
    private fun onConnectionFutureDone(future: Future<MediaController>) {
        if (connectionFuture === future) {
            connectionFuture = null
        }
        val connected = runCatching { future.get() }.getOrNull()
        when {
            // Liberación intencional: se descarta cualquier controlador que llegue tarde.
            released -> connected?.release()
            // Hay otra conexión vigente (defensivo): no interferir con ella.
            connectionFuture != null -> connected?.release()
            connected == null -> onConnectionFailed()
            else -> onConnected(connected)
        }
    }

    override fun setQueue(songs: List<Song>, startIndex: Int) =
        dispatch(Command.SetQueue(songs, startIndex))

    override fun play() = dispatch(Command.Play)

    override fun pause() = dispatch(Command.Pause)

    override fun togglePlayPause() = dispatch(Command.TogglePlayPause)

    override fun next() = dispatch(Command.Next)

    override fun previous() = dispatch(Command.Previous)

    override fun seekTo(positionMs: Long) = dispatch(Command.SeekTo(positionMs))

    /**
     * Libera el **cliente** (conexión con la sesión) y los listeners, no el
     * `ExoPlayer` del servicio. Idempotente. Si la conexión aún no había terminado,
     * cancela el future en lugar de dejar una conexión pendiente.
     */
    override fun release() {
        if (released) return
        released = true
        stopProgressUpdates()
        // Cancela el reintento automático pendiente antes de cerrar el ámbito.
        retryJob?.cancel()
        retryJob = null
        progressScope.cancel()
        pendingCommands.clear()
        optimisticQueue = null
        optimisticIndex = -1
        projectedQueue = emptyList()

        val current = controller
        if (current != null) {
            current.removeListener(playerListener)
            controller = null
            current.release()
        } else {
            // Aún conectando: `releaseFuture` cancela y libera el controlador cuando
            // llegue. Debe invocarse en el hilo de la aplicación (lo estamos).
            connectionFuture?.let { MediaController.releaseFuture(it) }
        }
        connectionFuture = null
        _state.value = PlaybackState()
    }

    /**
     * Aplica un comando: al reproductor si ya hay conexión, o a la cola de espera si
     * aún no la hay. Sin conexión, admite el comando según la fase (inicial o
     * recuperación) y asegura que hay un intento de conexión en curso.
     */
    private fun dispatch(command: Command) {
        if (released) return
        val current = controller
        if (current == null) {
            if (admitPendingCommand(command)) ensureConnectingOnDemand()
            return
        }
        execute(current, command)
    }

    /**
     * Decide si un comando sin conexión puede quedar en espera y con qué política:
     *
     * - **Conexión inicial** (aún no se estableció ninguna): se admite cualquier
     *   comando en orden FIFO, para conservar la secuencia validada `SetQueue` → `Play`.
     * - **Recuperación** (hubo conexión y se perdió): solo se admite una secuencia
     *   **anclada en `SetQueue`**, porque es la única que garantiza contexto sobre la
     *   sesión a la que se conecte. Un `Play`/`Next`/`Pause`/`SeekTo` suelto se
     *   descarta explícitamente: no se puede garantizar que tenga sentido sobre una
     *   sesión nueva o vacía. Un nuevo `SetQueue` reemplaza la secuencia anterior.
     *
     * El búfer de recuperación está acotado ([MaxRecoveryCommands]) y nunca descarta la
     * cabeza, de modo que `SetQueue` permanece junto a los comandos que lo siguen.
     */
    private fun admitPendingCommand(command: Command): Boolean {
        if (!initialConnectionEstablished) {
            pendingCommands.addLast(command)
            return true
        }
        if (command is Command.SetQueue) {
            pendingCommands.clear()
            pendingCommands.addLast(command)
            return true
        }
        if (pendingCommands.isNotEmpty() && pendingCommands.size < MaxRecoveryCommands) {
            pendingCommands.addLast(command)
            return true
        }
        return false
    }

    /**
     * Reintenta la conexión cuando hay una pérdida/fallo reales y no hay ya un intento
     * en curso ni un reintento automático programado. Evita conexiones simultáneas.
     */
    private fun ensureConnectingOnDemand() {
        if (released || !connectionLost) return
        if (connectionFuture != null) return
        if (retryJob != null) return
        connect()
    }

    /**
     * La conexión no produjo un `MediaController` utilizable (el servicio rechazó,
     * canceló o falló). No es una liberación intencional: si lo fuera, [released] ya
     * sería `true` y no se habría llegado aquí. Marca el estado y descarta los comandos
     * pendientes (C1), que no se ejecutarán. Un fallo de conexión inicial programa como
     * máximo un reintento diferido; si ese reintento también falla, no se reintenta solo.
     */
    private fun onConnectionFailed() {
        connectionLost = true
        pendingCommands.clear()
        if (!initialConnectionEstablished && !autoRetryUsed) {
            autoRetryUsed = true
            scheduleAutoRetry()
        }
    }

    /**
     * Programa el único reintento automático de conexión inicial. Se cancela en
     * [release] (explícitamente y con el ámbito de corrutinas). No bloquea el hilo.
     */
    private fun scheduleAutoRetry() {
        if (released || retryJob != null) return
        retryJob = progressScope.launch {
            delay(InitialConnectionRetryDelayMs)
            retryJob = null
            connect()
        }
    }

    /**
     * Conectado: registra el listener (una sola vez), vacía la cola de comandos en
     * orden y sincroniza el estado con la sesión real.
     */
    private fun onConnected(controller: MediaController) {
        if (released) {
            controller.release()
            return
        }
        this.controller = controller
        controller.addListener(playerListener)
        initialConnectionEstablished = true
        connectionLost = false
        retryJob?.cancel()
        retryJob = null
        while (pendingCommands.isNotEmpty()) {
            execute(controller, pendingCommands.removeFirst())
        }
        refreshProjectedQueue()
        syncState()
    }

    private fun execute(controller: MediaController, command: Command) {
        when (command) {
            is Command.SetQueue -> applySetQueue(controller, command.songs, command.startIndex)
            Command.Play -> {
                // Reanudar es una recuperación explícita: el error anterior ya no
                // describe el estado actual. Si Media3 informa de un error nuevo,
                // `onPlayerError` vuelve a fijar `lastError`. No se reintenta aquí.
                lastError = null
                controller.play()
                syncState()
            }

            Command.Pause -> {
                controller.pause()
                syncState()
            }

            Command.TogglePlayPause -> applyTogglePlayPause(controller)
            Command.Next -> applyNext(controller)
            Command.Previous -> applyPrevious(controller)
            is Command.SeekTo -> applySeekTo(controller, command.positionMs)
        }
    }

    private fun applySetQueue(controller: MediaController, songs: List<Song>, startIndex: Int) {
        lastError = null

        if (songs.isEmpty()) {
            optimisticQueue = null
            optimisticIndex = -1
            projectedQueue = emptyList()
            _state.value = PlaybackState()
            controller.clearMediaItems()
            stopProgressUpdates()
            return
        }

        val index = startIndex.coerceIn(0, songs.lastIndex)
        // Publicación optimista de la orden recién enviada; la timeline la confirmará.
        optimisticQueue = songs
        optimisticIndex = index
        _state.value = _state.value.copy(
            queue = songs,
            currentIndex = index,
            status = PlaybackStatus.IDLE,
            isPlaying = false,
            positionMs = 0L,
            durationMs = null,
            bufferedPositionMs = null,
            // La selección anterior pertenece a la pista anterior: se descarta hasta
            // que el reproductor informe de la nueva selección.
            selectedTrackFormat = null,
            decoderInputFormat = null,
            audioTrackConfig = null,
            error = null
        )

        controller.setMediaItems(songs.map { it.toSessionMediaItem() }, index, 0L)
        controller.prepare()
    }

    private fun applyTogglePlayPause(controller: MediaController) {
        if (controller.mediaItemCount == 0) return
        if (controller.playbackState == Player.STATE_ENDED) {
            // Tras ENDED, alternar solo por playWhenReady pausaría en vez de
            // reiniciar: se vuelve al inicio de la pista actual y se reproduce.
            // Reiniciar cuenta como reanudar: se descarta el error anterior.
            lastError = null
            controller.seekTo(0L)
            controller.play()
            syncState()
            return
        }
        if (controller.playWhenReady) {
            // Pausar no es una recuperación: el error, si lo hay, se conserva.
            controller.pause()
        } else {
            // Reanudar descarta el error anterior; un fallo nuevo se reemite por
            // `onPlayerError` y vuelve a fijar `lastError`.
            lastError = null
            controller.play()
        }
        syncState()
    }

    private fun applyNext(controller: MediaController) {
        if (!controller.hasNextMediaItem()) return
        controller.seekToNextMediaItem()
        syncState()
    }

    private fun applyPrevious(controller: MediaController) {
        if (!controller.hasPreviousMediaItem()) return
        controller.seekToPreviousMediaItem()
        syncState()
    }

    private fun applySeekTo(controller: MediaController, positionMs: Long) {
        if (controller.mediaItemCount == 0) return
        val duration = controller.duration
        val target = positionMs.coerceAtLeast(0L)
        val clamped = if (duration != C.TIME_UNSET && duration > 0L) {
            target.coerceAtMost(duration)
        } else {
            target
        }
        controller.seekTo(clamped)
        syncState()
    }

    /** Reconstruye [projectedQueue] desde la timeline actual del reproductor. */
    private fun refreshProjectedQueue() {
        val current = controller
        if (current == null) {
            projectedQueue = emptyList()
            return
        }
        val count = current.mediaItemCount
        if (count <= 0) {
            projectedQueue = emptyList()
            return
        }
        val songs = ArrayList<Song>(count)
        for (i in 0 until count) {
            songs.add(current.getMediaItemAt(i).toSessionSong())
        }
        projectedQueue = songs
    }

    /** Vuelca el estado real del reproductor a [PlaybackState] sin inventar progreso. */
    private fun syncState() {
        if (released) return
        val current = controller ?: return

        val optimistic = optimisticQueue
        val queue = optimistic ?: projectedQueue
        val hasItems = queue.isNotEmpty()
        val index = when {
            optimistic != null -> optimisticIndex
            hasItems -> current.currentMediaItemIndex
            else -> -1
        }
        val duration = current.duration.takeIf { it != C.TIME_UNSET && it >= 0L }
        val buffered = current.bufferedPosition.takeIf { it != C.TIME_UNSET && it >= 0L }
        val position = if (hasItems) current.currentPosition.coerceAtLeast(0L) else 0L

        _state.value = _state.value.copy(
            queue = queue,
            currentIndex = index,
            status = current.playbackState.toPlaybackStatus(),
            isPlaying = current.isPlaying,
            positionMs = position,
            durationMs = duration,
            bufferedPositionMs = buffered,
            selectedTrackFormat = mapSelectedTrackFormat(current.currentTracks),
            error = lastError
        )
        updateProgressUpdates()
    }

    /** Arranca o detiene el refresco periódico según haya reproducción activa. */
    private fun updateProgressUpdates() {
        val current = controller
        if (released || current == null || !current.isPlaying) {
            stopProgressUpdates()
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

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }
}

/** Comandos encolables antes de que el `MediaController` esté conectado. */
private sealed interface Command {
    data class SetQueue(val songs: List<Song>, val startIndex: Int) : Command
    data object Play : Command
    data object Pause : Command
    data object TogglePlayPause : Command
    data object Next : Command
    data object Previous : Command
    data class SeekTo(val positionMs: Long) : Command
}

private fun Int.toPlaybackStatus(): PlaybackStatus = when (this) {
    Player.STATE_BUFFERING -> PlaybackStatus.BUFFERING
    Player.STATE_READY -> PlaybackStatus.READY
    Player.STATE_ENDED -> PlaybackStatus.ENDED
    else -> PlaybackStatus.IDLE
}

/**
 * Mapea errores de Media3 a [PlaybackError] de dominio. Duplicado deliberadamente de
 * `Media3PlaybackController.kt` (allí es `private` por archivo) para no modificar ese
 * archivo; cualquier caso no contemplado cae en [PlaybackErrorCause.UNKNOWN].
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
    return PlaybackError(message = this.localizedMessage ?: errorCodeName, cause = cause)
}
