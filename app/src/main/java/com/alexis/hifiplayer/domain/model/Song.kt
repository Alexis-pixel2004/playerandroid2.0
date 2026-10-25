package com.alexis.hifiplayer.domain.model

data class Song(
    val id: Long = 0L,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val contentUri: String = "",
    val displayName: String = "",
    val fileExtension: String = "",
    val sizeBytes: Long = 0L,
    val mimeType: String = "",
    val dateModified: Long = 0L,
    /** Profundidad de bits del stream original, o `null` si no es verificable. */
    val bitDepth: Int? = null,
    /** Frecuencia de muestreo del stream original (Hz), o `null` si no es verificable. */
    val sampleRateHz: Int? = null,
    /** Número de canales del stream original, o `null` si no es verificable. */
    val channelCount: Int? = null,
    /** Bitrate del stream original (bits/s), o `null` si no es verificable. */
    val bitRate: Long? = null,
    /**
     * Códec del stream original. [AudioCodec.UNKNOWN] significa "no identificado",
     * no "sin códec".
     */
    val codec: AudioCodec = AudioCodec.UNKNOWN
) {
    /**
     * `true` si el códec está confirmado como lossless, `false` si está confirmado
     * como lossy, `null` si no se puede determinar. Se deriva de [codec]: no se
     * almacena para no duplicar estado ni quedar inconsistente con él.
     */
    val isLossless: Boolean? get() = codec.isLossless
}
