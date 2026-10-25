package com.alexis.hifiplayer.audio.mixer

import com.alexis.hifiplayer.domain.model.PcmEncoding

enum class MixerBehavior {
    MIXER,
    BIT_PERFECT,
    UNKNOWN
}

data class AudioMixerConfig(
    val sampleRateHz: Int,
    val channelCount: Int,
    val encoding: PcmEncoding,
    val behavior: MixerBehavior,
    val available: Boolean,
    val reasons: List<String> = emptyList(),
    val uncertainties: List<String> = emptyList()
)

object AudioMixerConfigDefaults {
    val UNKNOWN = AudioMixerConfig(
        sampleRateHz = 0,
        channelCount = 0,
        encoding = PcmEncoding.UNKNOWN,
        behavior = MixerBehavior.UNKNOWN,
        available = false
    )
}
