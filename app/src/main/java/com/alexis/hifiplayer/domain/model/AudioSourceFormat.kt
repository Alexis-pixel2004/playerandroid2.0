package com.alexis.hifiplayer.domain.model

/**
 * Represents the format of the original/source audio.
 */
data class AudioSourceFormat(
    val codec: AudioCodec = AudioCodec.UNKNOWN,
    val audioFormat: AudioFormat = AudioFormat(),
    val bitRate: Long? = null,
    val isLossless: Boolean? = null,
    val fileName: String? = null,
    val extension: String? = null
)
