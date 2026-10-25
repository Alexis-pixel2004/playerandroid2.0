package com.alexis.hifiplayer.audio.diagnostics

import com.alexis.hifiplayer.audio.format.AudioFormatMatchStatus
import com.alexis.hifiplayer.audio.mixer.MixerBehavior
import com.alexis.hifiplayer.domain.model.AudioDevice
import com.alexis.hifiplayer.domain.model.AudioDeviceCapabilities
import com.alexis.hifiplayer.domain.model.AudioFormat
import com.alexis.hifiplayer.domain.model.AudioOutputFormat
import com.alexis.hifiplayer.domain.model.AudioSourceFormat
import com.alexis.hifiplayer.domain.model.CapabilityState
import com.alexis.hifiplayer.domain.model.PcmEncoding
import com.alexis.hifiplayer.domain.model.ProcessingState
import org.junit.Assert.*
import org.junit.Test

class AudioPathAnalyzerTest {
    private val analyzer = AudioPathAnalyzer()

    @Test
    fun analyze_basic_conservative() {
        val src = AudioSourceFormat(audioFormat = AudioFormat(sampleRateHz = 44100, bitDepth = 16, channelCount = 2, encoding = PcmEncoding.PCM_16))
        val outReq = AudioOutputFormat(audioFormat = src.audioFormat)
        val caps = AudioDeviceCapabilities(
            supportedSampleRates = listOf(44100),
            supportedEncodings = listOf(PcmEncoding.PCM_16),
            supportedChannelCounts = listOf(2),
            capabilityState = CapabilityState.KNOWN
        )
        val device = AudioDevice(isActive = true)
        val path = com.alexis.hifiplayer.domain.model.AudioPathInfo(
            audioDevice = device,
            deviceCapabilities = caps,
            usesMixer = ProcessingState.UNKNOWN,
            hasResampling = ProcessingState.UNKNOWN,
            hasDsp = ProcessingState.UNKNOWN,
            hasDownmix = ProcessingState.UNKNOWN
        )
        val state = analyzer.analyze(
            sourceFormat = src,
            requestedOutput = outReq,
            pathInfo = path,
            configuredFormat = src.audioFormat,
            audioTrackInitialized = false,
            mixerBehavior = MixerBehavior.UNKNOWN,
            mixerAttributesAvailable = false
        )
        assertFalse(state.effectiveFormatKnown)
        assertEquals(MixerBehavior.UNKNOWN, state.mixerBehavior)
        assertFalse(state.audioTrackInitialized)
        assertTrue(state.uncertainties.isNotEmpty())
    }
}
