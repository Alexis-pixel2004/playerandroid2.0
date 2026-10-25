package com.alexis.hifiplayer.audio.diagnostics

import com.alexis.hifiplayer.audio.mixer.MixerBehavior
import com.alexis.hifiplayer.domain.model.AudioDevice
import com.alexis.hifiplayer.domain.model.AudioDeviceCapabilities
import com.alexis.hifiplayer.domain.model.AudioFormat
import com.alexis.hifiplayer.domain.model.AudioOutputFormat
import com.alexis.hifiplayer.domain.model.AudioPathInfo
import com.alexis.hifiplayer.domain.model.AudioSourceFormat
import com.alexis.hifiplayer.domain.model.BitPerfectStatus
import com.alexis.hifiplayer.domain.model.CapabilityState
import com.alexis.hifiplayer.domain.model.PcmEncoding
import com.alexis.hifiplayer.domain.model.ProcessingState
import com.alexis.hifiplayer.domain.usecase.DetectBitPerfectUseCase
import com.alexis.hifiplayer.domain.usecase.GetAudioDiagnosticsUseCase
import org.junit.Assert.*
import org.junit.Test

class AudioDiagnosticsCollectorTest {
    @Test
    fun effectiveUnknown_noBitPerfect() {
        val collector = AudioDiagnosticsCollector(
            DetectBitPerfectUseCase(),
            GetAudioDiagnosticsUseCase(DetectBitPerfectUseCase())
        )
        val src = AudioSourceFormat(audioFormat = AudioFormat(sampleRateHz = 44100, bitDepth = 16, channelCount = 2, encoding = PcmEncoding.PCM_16))
        val out = AudioOutputFormat(audioFormat = src.audioFormat)
        val caps = AudioDeviceCapabilities(
            supportedSampleRates = listOf(44100),
            supportedEncodings = listOf(PcmEncoding.PCM_16),
            supportedChannelCounts = listOf(2),
            capabilityState = CapabilityState.KNOWN
        )
        val path = AudioPathInfo(
            audioDevice = AudioDevice(isActive = true),
            deviceCapabilities = caps,
            usesMixer = ProcessingState.UNKNOWN,
            hasResampling = ProcessingState.UNKNOWN,
            hasDsp = ProcessingState.UNKNOWN,
            hasDownmix = ProcessingState.UNKNOWN
        )
        val diag = collector.collect(
            sourceFormat = src,
            requestedOutput = out,
            pathInfo = path,
            configuredFormat = src.audioFormat,
            audioTrackInitialized = true,
            mixerBehavior = MixerBehavior.UNKNOWN,
            mixerAttributesAvailable = false
        )
        assertFalse(diag.audioPathInfo.uncertainties.isEmpty())
        assertNotEquals(BitPerfectStatus.BIT_PERFECT, diag.status)
    }
}
