package com.alexis.hifiplayer.domain.model

/**
 * Formato de la pista de audio que el motor de reproducción (Media3/ExoPlayer)
 * **selecciona** para la pista actual.
 *
 * Describe el stream tal como lo declara el contenedor/archivo: para audio comprimido
 * es el formato **comprimido** (p. ej. `audio/flac`), no el PCM decodificado. Por eso
 * no permite afirmar el formato efectivo recibido por el DAC ni bit-perfect.
 *
 * Convención de honestidad: un dato no disponible es `null`; nunca se sustituye por
 * `0`, por valores negativos ni por el centinela `NO_VALUE` de Media3.
 *
 * Es agnóstico de Android: la capa `data/` traduce los tipos de Media3 a este modelo.
 */
sealed interface SelectedTrackFormat {

    /**
     * Selección única y determinable: exactamente una pista de audio está seleccionada.
     */
    data class Single(
        /** Códec derivado del MIME de la muestra; [AudioCodec.UNKNOWN] si no se identifica. */
        val codec: AudioCodec = AudioCodec.UNKNOWN,
        /** MIME de la muestra declarado por Media3, o `null` si no lo expone. */
        val mimeType: String? = null,
        /** Frecuencia de muestreo declarada (Hz), o `null` si ausente o no positiva. */
        val sampleRateHz: Int? = null,
        /** Número de canales declarado, o `null` si ausente o no positivo. */
        val channelCount: Int? = null,
        /**
         * Profundidad de bits, solo si Media3 la declara (habitual en PCM crudo); `null`
         * en la mayoría de formatos comprimidos.
         */
        val bitDepth: Int? = null,
        /** Bitrate declarado (bits/s), o `null` si ausente o no positivo. */
        val bitRate: Long? = null
    ) : SelectedTrackFormat

    /**
     * Hay más de una pista de audio seleccionada a la vez y no se puede determinar una
     * única. Se representa de forma explícita en lugar de elegir una arbitrariamente.
     */
    data class Ambiguous(
        /** Número de pistas de audio seleccionadas simultáneamente. */
        val selectedTrackCount: Int
    ) : SelectedTrackFormat
}
