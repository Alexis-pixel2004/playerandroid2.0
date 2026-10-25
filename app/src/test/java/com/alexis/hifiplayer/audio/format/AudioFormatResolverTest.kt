package com.alexis.hifiplayer.audio.format

import com.alexis.hifiplayer.domain.model.AudioDeviceCapabilities
import com.alexis.hifiplayer.domain.model.AudioFormat
import com.alexis.hifiplayer.domain.model.AudioSourceFormat
import com.alexis.hifiplayer.domain.model.PcmEncoding
import org.junit.Assert.*
import org.junit.Test

class AudioFormatResolverTest {
    @Test
    fun exactResolution() {
        val res = AudioFormatResolver().resolve(
            source = AudioSourceFormat(audioFormat = AudioFormat(sampleRateHz = 44100, bitDepth = 16, channelCount = 2, encoding = PcmEncoding.PCM_16)),
            capabilities = AudioDeviceCapabilities(
                supportedSampleRates = listOf(44100, 48000),
                supportedEncodings = listOf(PcmEncoding.PCM_16),
                supportedChannelCounts = listOf(2)
            )
        )
        assertEquals(AudioFormatResolutionStatus.EXACT, res.status)
        assertEquals(44100, res.configured.sampleRateHz)
        assertEquals(PcmEncoding.PCM_16, res.configured.encoding)
        assertEquals(2, res.configured.channelCount)
    }

    @Test
    fun capabilitiesUnknown() {
        val res = AudioFormatResolver().resolve(
            source = AudioSourceFormat(audioFormat = AudioFormat(sampleRateHz = 44100, bitDepth = 16, channelCount = 2, encoding = PcmEncoding.PCM_16)),
            capabilities = AudioDeviceCapabilities()
        )
        assertEquals(AudioFormatResolutionStatus.UNKNOWN, res.status)
    }

    @Test
    fun pcm24Unsupported() {
        val res = AudioFormatResolver().resolve(
            source = AudioSourceFormat(audioFormat = AudioFormat(sampleRateHz = 96000, bitDepth = 24, channelCount = 2, encoding = PcmEncoding.PCM_24)),
            capabilities = AudioDeviceCapabilities(supportedSampleRates = listOf(96000), supportedEncodings = listOf(PcmEncoding.PCM_16), supportedChannelCounts = listOf(2))
        )
        assertEquals(AudioFormatResolutionStatus.UNSUPPORTED, res.status)
    }
}
