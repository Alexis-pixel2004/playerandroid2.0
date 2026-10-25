package com.alexis.hifiplayer.audio.output

import com.alexis.hifiplayer.domain.model.AudioFormat
import com.alexis.hifiplayer.domain.model.AudioOutputFormat
import com.alexis.hifiplayer.domain.model.PcmEncoding
import org.junit.Assert.*
import org.junit.Test

class AudioTrackOutputProviderTest {
    @Test
    fun creationResult_initializes() {
        val res = AudioTrackCreationResult(
            status = AudioTrackCreationStatus.SUCCESS,
            requestedSampleRate = 44100,
            requestedChannelCount = 2,
            requestedEncoding = PcmEncoding.PCM_16.name
        )
        assertEquals(AudioTrackCreationStatus.SUCCESS, res.status)
        assertEquals(44100, res.requestedSampleRate)
    }

    @Test
    fun invalidFormat_cases() {
        assertNotEquals(AudioTrackCreationStatus.SUCCESS, AudioTrackCreationStatus.INVALID_FORMAT)
        assertNotEquals(AudioTrackCreationStatus.SUCCESS, AudioTrackCreationStatus.UNSUPPORTED_FORMAT)
    }

    @Test
    fun audioFormat_domain_valid() {
        val fmt = AudioFormat(sampleRateHz = 48000, channelCount = 2, bitDepth = 16, encoding = PcmEncoding.PCM_16)
        val out = AudioOutputFormat(audioFormat = fmt)
        assertEquals(48000, out.audioFormat.sampleRateHz)
        assertEquals(2, out.audioFormat.channelCount)
        assertEquals(PcmEncoding.PCM_16, out.audioFormat.encoding)
    }
}
