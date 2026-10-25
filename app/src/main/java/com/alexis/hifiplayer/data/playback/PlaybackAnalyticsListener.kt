package com.alexis.hifiplayer.data.playback

import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ControllerInfo
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.alexis.hifiplayer.data.media.AudioCodecMapper
import com.alexis.hifiplayer.domain.model.AudioCodec
import com.alexis.hifiplayer.domain.model.AudioTrackConfig
import com.alexis.hifiplayer.domain.model.DecoderInputFormat
import com.alexis.hifiplayer.domain.model.PcmEncoding
import com.google.common.util.concurrent.Futures

/**
 * Adaptador que registra un [AnalyticsListener] en el [ExoPlayer] del servicio
 * y propaga los eventos de diagnóstico al [MediaSession] para que lleguen a los
 * [MediaController] conectados mediante comandos personalizados.
 *
 * Vive en `data/` porque usa tipos de Media3 ([AnalyticsListener], [Format],
 * [AudioSink.AudioTrackConfig], [MediaSession]). El dominio no conoce estas APIs.
 *
 * Eventos manejados:
 * - `onAudioInputFormatChanged` → [DecoderInputFormat] (API estable)
 * - `onAudioTrackInitialized` → [AudioTrackConfig] (@UnstableApi, aislado aquí)
 *
 * Comunicación: el servicio envía comandos personalizados a cada controlador
 * conectado mediante [MediaSession.sendCustomCommand]. El controlador los recibe
 * en [MediaController.Listener.onCustomCommand] y actualiza [PlaybackState].
 */

/** Claves del Bundle para los argumentos del comando. */
internal object Keys {
    const val CODEC = "codec"
    const val MIME_TYPE = "mimeType"
    const val SAMPLE_RATE_HZ = "sampleRateHz"
    const val CHANNEL_COUNT = "channelCount"
    const val BIT_DEPTH = "bitDepth"
    const val BIT_RATE = "bitRate"
    const val ENCODING = "encoding"
    const val TRACK_SAMPLE_RATE_HZ = "trackSampleRateHz"
    const val CHANNEL_CONFIG = "channelConfig"
    const val BUFFER_SIZE_BYTES = "bufferSizeBytes"
    const val TUNNELING = "tunneling"
    const val OFFLOAD = "offload"
    const val TIMESTAMP_MS = "timestampMs"
}

class PlaybackAnalyticsListener(
    private val mediaSession: MediaSession,
    private val exoPlayer: ExoPlayer
) {

    /** Comando personalizado para actualizar el formato de entrada al decodificador. */
    private val COMMAND_DECODER_INPUT_FORMAT = SessionCommand(
        "com.alexis.hifiplayer.COMMAND_DECODER_INPUT_FORMAT",
        Bundle()
    )

    /** Comando personalizado para actualizar la configuración del AudioTrack. */
    private val COMMAND_AUDIO_TRACK_CONFIG = SessionCommand(
        "com.alexis.hifiplayer.COMMAND_AUDIO_TRACK_CONFIG",
        Bundle()
    )

    private val listener = object : AnalyticsListener {
        override fun onAudioInputFormatChanged(
            eventTime: EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?
        ) {
            val domainFormat = mapDecoderInputFormat(format)
            sendToAllControllers(COMMAND_DECODER_INPUT_FORMAT, domainFormat.toBundle())
        }

        @UnstableApi
        override fun onAudioTrackInitialized(
            eventTime: EventTime,
            audioTrackConfig: AudioSink.AudioTrackConfig
        ) {
            val domainConfig = mapAudioTrackConfig(audioTrackConfig)
            sendToAllControllers(COMMAND_AUDIO_TRACK_CONFIG, domainConfig.toBundle())
        }

        override fun onAudioTrackReleased(
            eventTime: EventTime,
            audioTrackConfig: AudioSink.AudioTrackConfig
        ) {
            val emptyBundle = Bundle()
            sendToAllControllers(COMMAND_AUDIO_TRACK_CONFIG, emptyBundle)
        }
    }

    /**
     * Inicia la escucha. Debe llamarse tras crear el ExoPlayer y la MediaSession.
     */
    fun start() {
        exoPlayer.addAnalyticsListener(listener)
    }

    /**
     * Detiene la escucha y libera recursos. Debe llamarse en onDestroy del servicio.
     */
    fun stop() {
        exoPlayer.removeAnalyticsListener(listener)
    }

    /**
     * Envía un comando personalizado a **todos** los controladores conectados.
     * Cada controlador lo recibirá en su `MediaController.Listener.onCustomCommand`.
     */
    private fun sendToAllControllers(command: SessionCommand, args: Bundle) {
        val controllers = mediaSession.connectedControllers
        for (controller in controllers) {
            mediaSession.sendCustomCommand(controller, command, args)
        }
    }

    /** Convierte [Format] de Media3 a [DecoderInputFormat] de dominio. */
    private fun mapDecoderInputFormat(format: Format): DecoderInputFormat = DecoderInputFormat(
        codec = AudioCodecMapper.fromMime(format.sampleMimeType),
        mimeType = format.sampleMimeType?.takeIf { it.isNotBlank() },
        sampleRateHz = format.sampleRate.takeIf { it > 0 },
        channelCount = format.channelCount.takeIf { it > 0 },
        bitDepth = pcmEncodingToBitDepth(format.pcmEncoding),
        bitRate = format.bitrate.takeIf { it > 0 }?.toLong()
    )

    /** Convierte [AudioSink.AudioTrackConfig] de Media3 a [AudioTrackConfig] de dominio. */
    @UnstableApi
    private fun mapAudioTrackConfig(config: AudioSink.AudioTrackConfig): AudioTrackConfig {
        val encoding = mediaEncodingToPcmEncoding(config.encoding)
        val channelCount = channelConfigToChannelCount(config.channelConfig)
        return AudioTrackConfig(
            encoding = encoding,
            sampleRateHz = config.sampleRate.takeIf { it > 0 },
            channelCount = channelCount,
            bufferSizeBytes = config.bufferSize.takeIf { it > 0 },
            tunneling = config.tunneling,
            offload = config.offload
        )
    }

    private fun mediaEncodingToPcmEncoding(encoding: Int): PcmEncoding = when (encoding) {
        C.ENCODING_PCM_8BIT -> PcmEncoding.PCM_16
        C.ENCODING_PCM_16BIT -> PcmEncoding.PCM_16
        C.ENCODING_PCM_24BIT -> PcmEncoding.PCM_24
        C.ENCODING_PCM_32BIT -> PcmEncoding.PCM_32
        C.ENCODING_PCM_FLOAT -> PcmEncoding.FLOAT
        else -> PcmEncoding.UNKNOWN
    }

    /** Traduce `AudioFormat.CHANNEL_OUT_*` / `channelMask` a número de canales. */
    private fun channelConfigToChannelCount(channelConfig: Int): Int? {
        if (channelConfig <= 0) return null
        return java.lang.Integer.bitCount(channelConfig)
    }
}

/** Extensiones para serializar modelos de dominio a Bundle (para comandos personalizados). */
private fun DecoderInputFormat.toBundle(): Bundle = Bundle().apply {
    putString(Keys.CODEC, codec.name)
    putString(Keys.MIME_TYPE, mimeType)
    putInt(Keys.SAMPLE_RATE_HZ, sampleRateHz ?: 0)
    putInt(Keys.CHANNEL_COUNT, channelCount ?: 0)
    putInt(Keys.BIT_DEPTH, bitDepth ?: 0)
    putLong(Keys.BIT_RATE, bitRate ?: 0L)
}

private fun AudioTrackConfig.toBundle(): Bundle = Bundle().apply {
    putString(Keys.ENCODING, encoding.name)
    putInt(Keys.TRACK_SAMPLE_RATE_HZ, sampleRateHz ?: 0)
    putInt(Keys.CHANNEL_CONFIG, channelCount ?: 0)
    putInt(Keys.BUFFER_SIZE_BYTES, bufferSizeBytes ?: 0)
    putBoolean(Keys.TUNNELING, tunneling)
    putBoolean(Keys.OFFLOAD, offload)
}

/** Extensiones para deserializar Bundle a modelos de dominio. */
internal fun Bundle.toDecoderInputFormat(): DecoderInputFormat? {
    if (isEmpty || (getInt(Keys.SAMPLE_RATE_HZ, 0) == 0 &&
        getInt(Keys.CHANNEL_COUNT, 0) == 0 &&
        getInt(Keys.BIT_DEPTH, 0) == 0 &&
        getLong(Keys.BIT_RATE, 0L) == 0L &&
        getString(Keys.CODEC)?.isNullOrBlank() != false &&
        getString(Keys.MIME_TYPE)?.isNullOrBlank() != false)) {
        return null
    }
    return DecoderInputFormat(
        codec = enumValueOfOrNull<AudioCodec>(getString(Keys.CODEC) ?: "") ?: AudioCodec.UNKNOWN,
        mimeType = getString(Keys.MIME_TYPE)?.takeIf { it.isNotBlank() },
        sampleRateHz = getInt(Keys.SAMPLE_RATE_HZ).takeIf { it > 0 },
        channelCount = getInt(Keys.CHANNEL_COUNT).takeIf { it > 0 },
        bitDepth = getInt(Keys.BIT_DEPTH).takeIf { it > 0 },
        bitRate = getLong(Keys.BIT_RATE).takeIf { it > 0 }
    )
}

internal fun Bundle.toAudioTrackConfig(): AudioTrackConfig? {
    if (isEmpty || (getInt(Keys.TRACK_SAMPLE_RATE_HZ, 0) == 0 &&
        getInt(Keys.CHANNEL_CONFIG, 0) == 0 &&
        getInt(Keys.BUFFER_SIZE_BYTES, 0) == 0 &&
        getString(Keys.ENCODING)?.isNullOrBlank() != false)) {
        return null
    }
    return AudioTrackConfig(
        encoding = enumValueOfOrNull<PcmEncoding>(getString(Keys.ENCODING) ?: "") ?: PcmEncoding.UNKNOWN,
        sampleRateHz = getInt(Keys.TRACK_SAMPLE_RATE_HZ).takeIf { it > 0 },
        channelCount = getInt(Keys.CHANNEL_CONFIG).takeIf { it > 0 },
        bufferSizeBytes = getInt(Keys.BUFFER_SIZE_BYTES).takeIf { it > 0 },
        tunneling = getBoolean(Keys.TUNNELING),
        offload = getBoolean(Keys.OFFLOAD)
    )
}

/** Helper para convertir String a enum de forma segura (Kotlin 1.9+). */
private inline fun <reified E : Enum<E>> enumValueOfOrNull(name: String): E? =
    try { enumValueOf<E>(name) } catch (_: IllegalArgumentException) { null }