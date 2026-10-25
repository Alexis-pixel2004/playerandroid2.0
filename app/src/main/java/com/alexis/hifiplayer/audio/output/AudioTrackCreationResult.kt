package com.alexis.hifiplayer.audio.output

enum class AudioTrackCreationStatus {
    SUCCESS,
    INVALID_FORMAT,
    UNSUPPORTED_FORMAT,
    MIXER_ATTRIBUTES_UNAVAILABLE,
    INITIALIZATION_FAILED,
    UNKNOWN_ERROR
}

data class AudioTrackCreationResult(
    val status: AudioTrackCreationStatus,
    val audioTrack: android.media.AudioTrack? = null,
    val reason: String? = null,
    val warnings: List<String> = emptyList(),
    val requestedSampleRate: Int = 0,
    val requestedChannelCount: Int = 0,
    val requestedEncoding: String = "",
    val bufferSizeInBytes: Int = 0,
    val mixerAttributesApplied: Boolean = false
)
