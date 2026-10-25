package com.alexis.hifiplayer.audio.diagnostics

import com.alexis.hifiplayer.domain.model.AudioDevice
import com.alexis.hifiplayer.domain.model.AudioDeviceCapabilities
import com.alexis.hifiplayer.domain.model.AudioFormat
import com.alexis.hifiplayer.domain.model.AudioOutputFormat
import com.alexis.hifiplayer.domain.model.PcmEncoding
import com.alexis.hifiplayer.domain.model.ProcessingState

data class AudioPathEffectiveFormat(
    val requested: AudioOutputFormat = AudioOutputFormat(),
    val configured: AudioFormat = AudioFormat(),
    val effective: AudioFormat = AudioFormat(sampleRateHz = 0, bitDepth = 0, channelCount = 0, encoding = PcmEncoding.UNKNOWN),
    val effectiveKnown: Boolean = false
)

data class AudioPathState(
    val sourceFormatPresent: Boolean = false,
    val requestedOutputFormat: AudioOutputFormat = AudioOutputFormat(),
    val configuredFormat: AudioFormat = AudioFormat(),
    val effectiveFormat: AudioFormat = AudioFormat(sampleRateHz = 0, bitDepth = 0, channelCount = 0, encoding = PcmEncoding.UNKNOWN),
    val effectiveFormatKnown: Boolean = false,
    val audioDevice: AudioDevice = AudioDevice(),
    val deviceCapabilities: AudioDeviceCapabilities = AudioDeviceCapabilities(),
    val audioTrackInitialized: Boolean = false,
    val audioTrackPlayState: Int? = null,
    val audioTrackState: Int? = null,
    val audioTrackBufferSizeBytes: Int? = null,
    val audioTrackSampleRate: Int? = null,
    val mixerBehaviorKnown: Boolean = false,
    val mixerBehavior: com.alexis.hifiplayer.audio.mixer.MixerBehavior = com.alexis.hifiplayer.audio.mixer.MixerBehavior.UNKNOWN,
    val mixerAttributesAvailable: Boolean = false,
    val hasResampling: ProcessingState = ProcessingState.UNKNOWN,
    val hasDsp: ProcessingState = ProcessingState.UNKNOWN,
    val hasDownmix: ProcessingState = ProcessingState.UNKNOWN,
    val usesMixer: ProcessingState = ProcessingState.UNKNOWN,
    val formatMatchStatus: com.alexis.hifiplayer.audio.format.AudioFormatMatchStatus = com.alexis.hifiplayer.audio.format.AudioFormatMatchStatus.UNKNOWN,
    val reasons: List<String> = emptyList(),
    val uncertainties: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)
