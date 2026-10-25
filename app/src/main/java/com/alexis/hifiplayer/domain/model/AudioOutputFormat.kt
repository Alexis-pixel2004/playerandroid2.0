package com.alexis.hifiplayer.domain.model

/**
 * Represents the requested or actual output audio format.
 * This model is agnostic from AudioTrack/Android APIs.
 */
data class AudioOutputFormat(
    val audioFormat: AudioFormat = AudioFormat(),
    val isRequested: Boolean = true,
    val isActual: Boolean = false,
    val channelMask: Int? = null
)
