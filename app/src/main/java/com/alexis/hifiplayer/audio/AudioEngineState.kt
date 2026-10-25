package com.alexis.hifiplayer.audio

import com.alexis.hifiplayer.audio.format.AudioFormatResolution
import com.alexis.hifiplayer.audio.format.AudioFormatResolutionStatus
import com.alexis.hifiplayer.domain.model.AudioFormat
import com.alexis.hifiplayer.domain.model.AudioDiagnostics
import com.alexis.hifiplayer.domain.model.AudioOutputFormat
import com.alexis.hifiplayer.domain.model.AudioSourceFormat

enum class AudioEngineState {
    IDLE,
    PREPARED,
    PLAYING,
    PAUSED,
    STOPPED,
    RELEASED
}

data class AudioEngineConfig(
    val sourceFormat: AudioSourceFormat = AudioSourceFormat(),
    val outputFormat: AudioOutputFormat = AudioOutputFormat()
)

data class AudioEngineStatus(
    val state: AudioEngineState = AudioEngineState.IDLE,
    val audioTrackInitialized: Boolean = false,
    val audioTrackPlayState: Int? = null,
    val audioTrackState: Int? = null,
    val bufferSizeBytes: Int = 0,
    val sampleRate: Int? = null,
    val configuredFormat: AudioFormat = AudioFormat(),
    val outputFormat: AudioOutputFormat = AudioOutputFormat(),
    val sourceFormat: AudioSourceFormat = AudioSourceFormat(),
    val resolution: AudioFormatResolution? = null,
    val error: String? = null,
    val warnings: List<String> = emptyList()
)
