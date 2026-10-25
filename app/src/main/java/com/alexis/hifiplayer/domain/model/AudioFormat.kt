package com.alexis.hifiplayer.domain.model

/**
 * Represents a normalized PCM audio format.
 */
data class AudioFormat(
    val sampleRateHz: Int = 0,
    val bitDepth: Int = 0,
    val channelCount: Int = 0,
    val encoding: PcmEncoding = PcmEncoding.UNKNOWN
)
