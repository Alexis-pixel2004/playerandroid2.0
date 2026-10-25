package com.alexis.hifiplayer.audio.output

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.alexis.hifiplayer.audio.mixer.AudioMixerAttributesProvider
import com.alexis.hifiplayer.domain.model.AudioFormat as DomainAudioFormat
import com.alexis.hifiplayer.domain.model.AudioOutputFormat
import com.alexis.hifiplayer.domain.model.PcmEncoding

class AudioTrackOutputProvider(
    private val context: Context,
    private val mixerAttributesProvider: AudioMixerAttributesProvider
) {

    private val audioManager: AudioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var currentTrack: AudioTrack? = null
    private var lastBufferSizeBytes: Int = 0
    private var configuredDomainFormat: DomainAudioFormat = DomainAudioFormat()

    fun getConfiguredFormat(): DomainAudioFormat = configuredDomainFormat
    fun getLastBufferSizeBytes(): Int = lastBufferSizeBytes

    fun createOutput(outputFormat: AudioOutputFormat): AudioTrackCreationResult {
        val fmt = outputFormat.audioFormat
        return createForDomainFormat(fmt)
    }

    fun createForDomainFormat(domainFormat: DomainAudioFormat): AudioTrackCreationResult {
        release()
        val warnings = mutableListOf<String>()
        val sr = domainFormat.sampleRateHz
        val chCount = domainFormat.channelCount
        val encDom = domainFormat.encoding

        if (sr <= 0) {
            return AudioTrackCreationResult(
                status = AudioTrackCreationStatus.INVALID_FORMAT,
                reason = "Invalid sample rate: "
            )
        }
        if (chCount <= 0) {
            return AudioTrackCreationResult(
                status = AudioTrackCreationStatus.INVALID_FORMAT,
                reason = "Invalid channel count: "
            )
        }
        if (encDom == PcmEncoding.UNKNOWN) {
            return AudioTrackCreationResult(
                status = AudioTrackCreationStatus.INVALID_FORMAT,
                reason = "Encoding unknown"
            )
        }
        val encAndroid = mapEncoding(encDom) ?: run {
            return AudioTrackCreationResult(
                status = AudioTrackCreationStatus.UNSUPPORTED_FORMAT,
                reason = "Unsupported encoding: "
            )
        }
        val chanMask = mapChannelMask(chCount) ?: run {
            return AudioTrackCreationResult(
                status = AudioTrackCreationStatus.UNSUPPORTED_FORMAT,
                reason = "Unsupported channel count: "
            )
        }
        val audioFmt = AudioFormat.Builder()
            .setSampleRate(sr)
            .setEncoding(encAndroid)
            .setChannelMask(chanMask)
            .build()

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        var bufferSize = AudioTrack.getMinBufferSize(sr, chanMask, encAndroid)
        if (bufferSize <= 0) {
            when (bufferSize) {
                AudioTrack.ERROR -> {
                    bufferSize = sr * chCount * 2
                    warnings.add("getMinBufferSize returned ERROR, using fallback: ")
                }
                AudioTrack.ERROR_BAD_VALUE -> {
                    bufferSize = sr * chCount * 2
                    warnings.add("getMinBufferSize returned ERROR_BAD_VALUE, using fallback: ")
                }
                else -> {
                    bufferSize = sr * chCount * 2
                    warnings.add("getMinBufferSize returned , using fallback: ")
                }
            }
        }
        lastBufferSizeBytes = bufferSize
        configuredDomainFormat = domainFormat

        val builder = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(audioFmt)
            .setBufferSizeInBytes(bufferSize)

        val mixerAttrs = mixerAttributesProvider.buildRequestedMixerAttributes(sr, chCount, encDom)
        if (mixerAttrs != null) {
            warnings.add("AudioMixerAttributes built but not applied to AudioTrack.Builder (API not available)")
        } else {
            warnings.add("AudioMixerAttributes not available for requested format")
        }

        val track = try {
            builder.build()
        } catch (e: Exception) {
            return AudioTrackCreationResult(
                status = AudioTrackCreationStatus.INITIALIZATION_FAILED,
                reason = "Failed to build AudioTrack: ",
                requestedSampleRate = sr,
                requestedChannelCount = chCount,
                requestedEncoding = encDom.name,
                bufferSizeInBytes = bufferSize,
                mixerAttributesApplied = false
            )
        }

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            return AudioTrackCreationResult(
                status = AudioTrackCreationStatus.INITIALIZATION_FAILED,
                reason = "AudioTrack state not initialized: ",
                requestedSampleRate = sr,
                requestedChannelCount = chCount,
                requestedEncoding = encDom.name,
                bufferSizeInBytes = bufferSize,
                mixerAttributesApplied = false
            )
        }

        currentTrack = track
        return AudioTrackCreationResult(
            status = AudioTrackCreationStatus.SUCCESS,
            audioTrack = track,
            requestedSampleRate = sr,
            requestedChannelCount = chCount,
            requestedEncoding = encDom.name,
            bufferSizeInBytes = bufferSize,
            mixerAttributesApplied = false,
            warnings = warnings
        )
    }

    fun start() {
        currentTrack?.takeIf { it.state == AudioTrack.STATE_INITIALIZED && it.playState != AudioTrack.PLAYSTATE_PLAYING }?.play()
    }

    fun pause() {
        currentTrack?.takeIf { it.playState == AudioTrack.PLAYSTATE_PLAYING }?.pause()
    }

    fun stop() {
        currentTrack?.takeIf { it.playState != AudioTrack.PLAYSTATE_STOPPED }?.stop()
    }

    fun release() {
        currentTrack?.release()
        currentTrack = null
        lastBufferSizeBytes = 0
        configuredDomainFormat = DomainAudioFormat()
    }

    fun getState(): Int? = currentTrack?.state
    fun getPlayState(): Int? = currentTrack?.playState
    fun getSampleRate(): Int? = currentTrack?.sampleRate
    fun getBufferSize(): Int? = if (lastBufferSizeBytes > 0) lastBufferSizeBytes else null

    private fun bytesPerSampleFor(encoding: Int): Int? = when (encoding) {
        AudioFormat.ENCODING_PCM_16BIT -> 2
        AudioFormat.ENCODING_PCM_8BIT -> 1
        AudioFormat.ENCODING_PCM_FLOAT -> 4
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
        AudioFormat.ENCODING_PCM_32BIT -> 4
        else -> null
    }

    private fun mapEncoding(enc: PcmEncoding): Int? = when (enc) {
        PcmEncoding.PCM_16 -> AudioFormat.ENCODING_PCM_16BIT
        PcmEncoding.PCM_24 -> null
        PcmEncoding.PCM_32 -> null
        PcmEncoding.FLOAT -> null
        PcmEncoding.UNKNOWN -> null
    }

    private fun mapChannelMask(count: Int): Int? = when (count) {
        1 -> AudioFormat.CHANNEL_OUT_MONO
        2 -> AudioFormat.CHANNEL_OUT_STEREO
        else -> null
    }
}
