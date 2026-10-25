package com.alexis.hifiplayer.data.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.alexis.hifiplayer.MainActivity

/**
 * Servicio de reproducción en segundo plano basado en Media3.
 *
 * **Estado actual: activo.** Es el `MediaSessionService` de la ruta de reproducción
 * vigente: mantiene el **único** [ExoPlayer] que reproduce música y lo expone mediante
 * la [MediaSession]. La app se conecta a él con `MediaSessionPlaybackController`
 * (`MediaController`) al crear el `PlaybackViewModel`; la reproducción no ocurre en la
 * actividad.
 *
 * Responsabilidades:
 * - [MediaSessionService] liga y administra su propio ciclo de vida como servicio.
 * - [ExoPlayer] es el **único** motor de la ruta activa; el servicio es su propietario.
 * - [MediaSession] envuelve ese mismo reproductor (no crea un reproductor nuevo) y
 *   aporta los controles del sistema, la notificación y la pantalla de bloqueo.
 * - [PlaybackAnalyticsListener] observa eventos de diagnóstico (formato entrada decodificador,
 *   configuración AudioTrack) y los propaga a los controladores vía comandos personalizados.
 *
 * Honestidad: este servicio no afirma bit-perfect ni inspecciona el formato efectivo
 * de salida; solo delega la reproducción en Media3.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private var analyticsListener: PlaybackAnalyticsListener? = null

    override fun onCreate() {
        super.onCreate()

        val audioAttributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val player: ExoPlayer = ExoPlayer.Builder(this)
            // handleAudioFocus = true: ExoPlayer solicita y cede el audio focus.
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            // Pausa automáticamente al desconectar auriculares/salida de audio.
            .setHandleAudioBecomingNoisy(true)
            .build()

        exoPlayer = player

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openAppPendingIntent())
            .build()

        // Inicia el listener de diagnósticos (Etapa 2b): observa formato entrada decodificador
        // y configuración AudioTrack, y los envía a los controladores conectados.
        analyticsListener = PlaybackAnalyticsListener(mediaSession!!, player)
        analyticsListener!!.start()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        // Libera el listener de diagnósticos antes que el reproductor.
        analyticsListener?.stop()
        analyticsListener = null

        // Libera el reproductor y la sesión en todos los casos; luego limpia la
        // referencia para no reutilizar una sesión ya liberada.
        mediaSession?.let { session ->
            session.player.release()
            session.release()
        }
        mediaSession = null
        exoPlayer = null
        super.onDestroy()
    }

    /**
     * `PendingIntent` que abre la app desde los controles del sistema, la notificación
     * o la pantalla de bloqueo. Reutiliza la tarea/tarea existente en lugar de crear
     * una nueva instancia de la actividad.
     */
    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            /* requestCode = */ 0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
