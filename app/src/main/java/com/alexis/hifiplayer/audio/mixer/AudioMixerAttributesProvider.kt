package com.alexis.hifiplayer.audio.mixer

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import com.alexis.hifiplayer.domain.model.PcmEncoding

class AudioMixerAttributesProvider(
    private val context: Context
) {

    private val audioManager: AudioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun buildRequestedMixerAttributes(
        sampleRateHz: Int,
        channelCount: Int,
        encoding: PcmEncoding
    ): AudioMixerAttributes? {
        val enc = mapPcmEncodingToAndroid(encoding) ?: return null
        val chan = mapChannelsToAndroid(channelCount) ?: return null
        val fmt = AudioFormat.Builder()
            .setSampleRate(sampleRateHz)
            .setEncoding(enc)
            .setChannelMask(chan)
            .build()
        return try {
            AudioMixerAttributes.Builder(fmt).build()
        } catch (_: Exception) {
            null
        }
    }

    private fun mapPcmEncodingToAndroid(enc: PcmEncoding): Int? = when (enc) {
        PcmEncoding.PCM_16 -> AudioFormat.ENCODING_PCM_16BIT
        PcmEncoding.PCM_24 -> null
        PcmEncoding.PCM_32 -> null
        PcmEncoding.FLOAT -> null
        PcmEncoding.UNKNOWN -> null
    }

    private fun mapChannelsToAndroid(count: Int): Int? = when (count) {
        1 -> AudioFormat.CHANNEL_OUT_MONO
        2 -> AudioFormat.CHANNEL_OUT_STEREO
        else -> null
    }
}
