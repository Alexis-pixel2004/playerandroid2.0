package com.alexis.hifiplayer.domain.model

/**
 * Represents the full known audio path from source to output.
 */
data class AudioPathInfo(
    val sourceFormat: AudioSourceFormat = AudioSourceFormat(),
    val decodedFormat: AudioFormat = AudioFormat(),
    val requestedOutputFormat: AudioOutputFormat? = null,
    val actualOutputFormat: AudioOutputFormat? = null,
    val audioDevice: AudioDevice = AudioDevice(),
    val deviceCapabilities: AudioDeviceCapabilities = AudioDeviceCapabilities(),
    val usesMixer: ProcessingState = ProcessingState.UNKNOWN,
    val mixerAttributesPresent: ProcessingState = ProcessingState.UNKNOWN,
    val hasResampling: ProcessingState = ProcessingState.UNKNOWN,
    val hasDsp: ProcessingState = ProcessingState.UNKNOWN,
    val hasDownmix: ProcessingState = ProcessingState.UNKNOWN,
    val isBitPerfectCapablePath: Boolean? = null,
    val routeInfo: String? = null,
    val uncertainties: List<String> = emptyList()
)
