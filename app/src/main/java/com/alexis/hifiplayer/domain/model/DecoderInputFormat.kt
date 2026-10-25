package com.alexis.hifiplayer.domain.model

/**
 * Formato de entrada al decodificador de audio observado por ExoPlayer
 * (callback `AnalyticsListener.onAudioInputFormatChanged`).
 *
 * Describe el stream **tal como llega al decodificador**: puede ser comprimido
 * (p. ej. `audio/flac`) o ya PCM si el contenedor lo entrega decodificado.
 * No es el formato configurado en AudioTrack ni el recibido por el DAC.
 *
 * Convención de honestidad: un dato no disponible es `null`; nunca se sustituye
 * por `0`, por valores negativos ni por el centinela `NO_VALUE` de Media3.
 *
 * Es agnóstico de Android: la capa `data/` traduce los tipos de Media3 a este modelo.
 */
data class DecoderInputFormat(
    /** Códec derivado del MIME de la muestra; [AudioCodec.UNKNOWN] si no se identifica. */
    val codec: AudioCodec = AudioCodec.UNKNOWN,
    /** MIME de la muestra declarado por Media3, o `null` si no lo expone. */
    val mimeType: String? = null,
    /** Frecuencia de muestreo declarada (Hz), o `null` si ausente o no positiva. */
    val sampleRateHz: Int? = null,
    /** Número de canales declarado, o `null` si ausente o no positivo. */
    val channelCount: Int? = null,
    /**
     * Profundidad de bits, solo si Media3 la declara (habitual en PCM crudo);
     * `null` en la mayoría de formatos comprimidos.
     */
    val bitDepth: Int? = null,
    /** Bitrate declarado (bits/s), o `null` si ausente o no positivo. */
    val bitRate: Long? = null
)