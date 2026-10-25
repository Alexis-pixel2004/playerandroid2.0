package com.alexis.hifiplayer.audio.format

import com.alexis.hifiplayer.domain.model.AudioDeviceCapabilities
import com.alexis.hifiplayer.domain.model.AudioFormat
import com.alexis.hifiplayer.domain.model.AudioSourceFormat
import com.alexis.hifiplayer.domain.model.PcmEncoding

enum class AudioFormatMatchStatus {
    EXACT_MATCH,
    PARTIAL_MATCH,
    NO_MATCH,
    UNKNOWN
}

data class AudioFormatMatchResult(
    val status: AudioFormatMatchStatus,
    val sampleRateMatch: Boolean? = null,
    val encodingMatch: Boolean? = null,
    val channelMatch: Boolean? = null,
    val bitDepthMatch: Boolean? = null,
    val reasons: List<String> = emptyList(),
    val uncertainties: List<String> = emptyList()
)

class AudioFormatMatcher {

    fun match(source: AudioSourceFormat, capabilities: AudioDeviceCapabilities): AudioFormatMatchResult =
        matchFormat(source.audioFormat, capabilities)

    fun match(sourceFormat: AudioFormat, capabilities: AudioDeviceCapabilities): AudioFormatMatchResult =
        matchFormat(sourceFormat, capabilities)

    private fun matchFormat(sourceFormat: AudioFormat, capabilities: AudioDeviceCapabilities): AudioFormatMatchResult {
        val uncertainties = mutableListOf<String>()
        val reasons = mutableListOf<String>()

        val sampleRates = capabilities.supportedSampleRates
        val encodings = capabilities.supportedEncodings
        val channels = capabilities.supportedChannelCounts

        if (sampleRates.isEmpty() && encodings.isEmpty() && channels.isEmpty()) {
            uncertainties.add("Device capabilities completely unknown")
            return AudioFormatMatchResult(
                status = AudioFormatMatchStatus.UNKNOWN,
                sampleRateMatch = null,
                encodingMatch = null,
                channelMatch = null,
                bitDepthMatch = null,
                reasons = reasons,
                uncertainties = uncertainties
            )
        }

        var srMatch: Boolean? = null
        sourceFormat.sampleRateHz?.let { sr ->
            if (sampleRates.isNotEmpty()) {
                srMatch = sampleRates.contains(sr)
                if (srMatch == false) {
                    reasons.add("Sample rate $sr not in supported sample rates")
                }
            } else {
                uncertainties.add("Sample rates not exposed")
            }
        } ?: run {
            uncertainties.add("Source sample rate unknown")
        }

        var encMatch: Boolean? = null
        val srcEnc = mapEncoding(sourceFormat)
        if (srcEnc != null) {
            if (encodings.isNotEmpty()) {
                encMatch = encodings.contains(srcEnc)
                if (encMatch == false) {
                    reasons.add("Encoding ${srcEnc.name} not supported")
                }
            } else {
                uncertainties.add("Encodings not exposed")
            }
        } else {
            uncertainties.add("Source encoding/bit depth not verifiable")
        }

        var chMatch: Boolean? = null
        sourceFormat.channelCount?.let { ch ->
            if (channels.isNotEmpty()) {
                chMatch = channels.contains(ch)
                if (chMatch == false) {
                    reasons.add("Channel count $ch not supported")
                }
            } else {
                uncertainties.add("Channel counts not exposed")
            }
        } ?: run {
            uncertainties.add("Source channel count unknown")
        }

        var bdMatch: Boolean? = null
        sourceFormat.bitDepth?.let {
            bdMatch = if (srcEnc != null && encodings.isNotEmpty()) encMatch else null
            if (encodings.isEmpty()) {
                uncertainties.add("Bit depth cannot be verified against encodings")
            }
        } ?: run {
            uncertainties.add("Source bit depth unknown")
        }

        val hasKnown = srMatch != null || encMatch != null || chMatch != null
        val allKnownTrue = (srMatch == null || srMatch) && (encMatch == null || encMatch) && (chMatch == null || chMatch)
        val anyFalse = srMatch == false || encMatch == false || chMatch == false

        val status = when {
            anyFalse -> AudioFormatMatchStatus.NO_MATCH
            !hasKnown -> AudioFormatMatchStatus.UNKNOWN
            allKnownTrue -> {
                if (srMatch != null && encMatch != null && chMatch != null) {
                    AudioFormatMatchStatus.EXACT_MATCH
                } else {
                    AudioFormatMatchStatus.PARTIAL_MATCH
                }
            }
            else -> AudioFormatMatchStatus.PARTIAL_MATCH
        }

        return AudioFormatMatchResult(
            status = status,
            sampleRateMatch = srMatch,
            encodingMatch = encMatch,
            channelMatch = chMatch,
            bitDepthMatch = bdMatch,
            reasons = reasons,
            uncertainties = uncertainties
        )
    }

    private fun mapEncoding(fmt: AudioFormat): PcmEncoding? {
        val enc = fmt.encoding
        if (enc != null) {
            return when (enc) {
                PcmEncoding.PCM_16 -> PcmEncoding.PCM_16
                PcmEncoding.PCM_24 -> null
                PcmEncoding.PCM_32 -> null
                PcmEncoding.FLOAT -> null
                PcmEncoding.UNKNOWN -> null
            }
        }
        fmt.bitDepth?.let { bd ->
            return when (bd) {
                16 -> PcmEncoding.PCM_16
                24 -> null
                32 -> null
                else -> null
            }
        }
        return null
    }
}
