package com.alexis.hifiplayer.domain.model

/**
 * Comprehensive diagnostics for UI/lab display.
 * Never assume unknown equals false.
 */
data class AudioDiagnostics(
    val status: BitPerfectStatus = BitPerfectStatus.UNAVAILABLE,
    val sourceFormat: AudioSourceFormat = AudioSourceFormat(),
    val decodedFormat: AudioFormat = AudioFormat(),
    val requestedOutputFormat: AudioOutputFormat? = null,
    val actualOutputFormat: AudioOutputFormat? = null,
    val audioDevice: AudioDevice = AudioDevice(),
    val deviceCapabilities: AudioDeviceCapabilities = AudioDeviceCapabilities(),
    val audioPathInfo: AudioPathInfo = AudioPathInfo(),
    val evidence: List<String> = emptyList(),
    val uncertainties: List<String> = emptyList(),
    val reasons: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val timestampMs: Long = 0L
)
