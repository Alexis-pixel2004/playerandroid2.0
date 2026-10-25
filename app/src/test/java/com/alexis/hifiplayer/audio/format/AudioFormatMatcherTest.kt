package com.alexis.hifiplayer.audio.format

import com.alexis.hifiplayer.domain.model.AudioDeviceCapabilities
import com.alexis.hifiplayer.domain.model.AudioFormat
import com.alexis.hifiplayer.domain.model.AudioSourceFormat
import com.alexis.hifiplayer.domain.model.PcmEncoding
import org.junit.Assert.*
import org.junit.Test

class AudioFormatMatcherTest {
    private val matcher = AudioFormatMatcher()

    @Test
    fun partial_encodingUnverifiable() {
        val source = AudioSourceFormat(audioFormat = AudioFormat(sampleRateHz = 96000, bitDepth = 24, channelCount = 2, encoding = PcmEncoding.UNKNOWN))
        val caps = AudioDeviceCapabilities(
            supportedSampleRates = listOf(96000),
            supportedEncodings = listOf(PcmEncoding.PCM_16),
            supportedChannelCounts = listOf(2)
        )
        val res = matcher.match(source, caps)
        assertNotEquals(AudioFormatMatchStatus.EXACT_MATCH, res.status)
        assertNull(res.encodingMatch)
    }
}
