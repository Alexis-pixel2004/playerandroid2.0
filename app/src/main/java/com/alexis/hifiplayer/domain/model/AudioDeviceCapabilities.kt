package com.alexis.hifiplayer.domain.model

/**
 * Represents audio device capabilities. Values may be unknown as Android does not always
 * expose full DAC capabilities.
 */
data class AudioDeviceCapabilities(
    val supportedSampleRates: List<Int> = emptyList(),
    val supportedEncodings: List<PcmEncoding> = emptyList(),
    val supportedChannelCounts: List<Int> = emptyList(),
    val maxBitDepth: Int? = null,
    val supportsBitPerfect: Boolean? = null,
    val isUsb: Boolean? = null,
    val capabilityState: CapabilityState = CapabilityState.UNKNOWN,
    val uncertaintyFlags: List<String> = emptyList()
)
