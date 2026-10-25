package com.alexis.hifiplayer.audio.format

import com.alexis.hifiplayer.domain.model.AudioDeviceCapabilities
import com.alexis.hifiplayer.domain.model.AudioFormat
import com.alexis.hifiplayer.domain.model.AudioOutputFormat
import com.alexis.hifiplayer.domain.model.AudioSourceFormat
import com.alexis.hifiplayer.domain.model.PcmEncoding

enum class AudioFormatResolutionStatus {
    EXACT,
    PARTIAL,
    INCOMPATIBLE,
    UNKNOWN,
    UNSUPPORTED
}

data class AudioFormatResolution(
    val status: AudioFormatResolutionStatus,
    val requested: AudioOutputFormat = AudioOutputFormat(),
    val configured: AudioFormat = AudioFormat(),
    val sourceFormat: AudioSourceFormat = AudioSourceFormat(),
    val matchStatus: AudioFormatMatchStatus = AudioFormatMatchStatus.UNKNOWN,
    val reasons: List<String> = emptyList(),
    val uncertainties: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

class AudioFormatResolver(
    private val matcher: AudioFormatMatcher = AudioFormatMatcher()
) {
    fun resolve(
        source: AudioSourceFormat,
        capabilities: AudioDeviceCapabilities
    ): AudioFormatResolution {
        val srcFmt = source.audioFormat
        val match = matcher.match(srcFmt, capabilities)
        val reasons = mutableListOf<String>()
        val uncertainties = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        reasons.addAll(match.reasons)
        uncertainties.addAll(match.uncertainties)

        if (capabilities.supportedSampleRates.isEmpty() &&
            capabilities.supportedEncodings.isEmpty() &&
            capabilities.supportedChannelCounts.isEmpty()
        ) {
            uncertainties.add("Capabilities unknown; cannot determine safe requested format")
            return AudioFormatResolution(
                status = AudioFormatResolutionStatus.UNKNOWN,
                sourceFormat = source,
                matchStatus = match.status,
                reasons = reasons,
                uncertainties = uncertainties,
                warnings = warnings
            )
        }

        val safeEnc = mapToSafeEncoding(srcFmt)
        if (safeEnc == null) {
            reasons.add("Source encoding not safely supported (only PCM_16 verified)")
            return AudioFormatResolution(
                status = AudioFormatResolutionStatus.UNSUPPORTED,
                sourceFormat = source,
                matchStatus = match.status,
                reasons = reasons,
                uncertainties = uncertainties,
                warnings = warnings
            )
        }
        if (capabilities.supportedEncodings.isNotEmpty() && !capabilities.supportedEncodings.contains(safeEnc)) {
            reasons.add("PCM_16 not in declared encodings")
            return AudioFormatResolution(
                status = AudioFormatResolutionStatus.INCOMPATIBLE,
                sourceFormat = source,
                matchStatus = match.status,
                reasons = reasons,
                uncertainties = uncertainties,
                warnings = warnings
            )
        }
        if (match.status == AudioFormatMatchStatus.NO_MATCH) {
            return AudioFormatResolution(
                status = AudioFormatResolutionStatus.INCOMPATIBLE,
                sourceFormat = source,
                matchStatus = match.status,
                reasons = reasons,
                uncertainties = uncertainties,
                warnings = warnings
            )
        }

        val sr = srcFmt.sampleRateHz?.takeIf { it > 0 && capabilities.supportedSampleRates.contains(it) } ?: run {
            if (capabilities.supportedSampleRates.isNotEmpty()) {
                reasons.add("No safe sample rate match for requested format")
                null
            } else {
                null
            }
        }
        val ch = srcFmt.channelCount?.takeIf { it > 0 && capabilities.supportedChannelCounts.contains(it) } ?: run {
            if (capabilities.supportedChannelCounts.isNotEmpty()) {
                reasons.add("No safe channel count match")
                null
            } else {
                null
            }
        }
        if (sr == null || ch == null) {
            if (match.status == AudioFormatMatchStatus.PARTIAL_MATCH) {
                uncertainties.add("Partial match; cannot derive exact safe requested format")
                return AudioFormatResolution(
                    status = AudioFormatResolutionStatus.PARTIAL,
                    sourceFormat = source,
                    matchStatus = match.status,
                    reasons = reasons,
                    uncertainties = uncertainties,
                    warnings = warnings
                )
            }
            return AudioFormatResolution(
                status = AudioFormatResolutionStatus.UNKNOWN,
                sourceFormat = source,
                matchStatus = match.status,
                reasons = reasons,
                uncertainties = uncertainties,
                warnings = warnings
            )
        }

        val configured = AudioFormat(sampleRateHz = sr, bitDepth = 16, channelCount = ch, encoding = safeEnc)
        val requested = AudioOutputFormat(audioFormat = configured)
        return when (match.status) {
            AudioFormatMatchStatus.EXACT_MATCH -> AudioFormatResolution(
                status = AudioFormatResolutionStatus.EXACT,
                requested = requested,
                configured = configured,
                sourceFormat = source,
                matchStatus = match.status,
                reasons = reasons,
                uncertainties = uncertainties,
                warnings = warnings
            )
            AudioFormatMatchStatus.PARTIAL_MATCH -> AudioFormatResolution(
                status = AudioFormatResolutionStatus.PARTIAL,
                requested = requested,
                configured = configured,
                sourceFormat = source,
                matchStatus = match.status,
                reasons = reasons,
                uncertainties = uncertainties,
                warnings = warnings
            )
            else -> AudioFormatResolution(
                status = AudioFormatResolutionStatus.UNKNOWN,
                requested = requested,
                configured = configured,
                sourceFormat = source,
                matchStatus = match.status,
                reasons = reasons,
                uncertainties = uncertainties,
                warnings = warnings
            )
        }
    }

    private fun mapToSafeEncoding(fmt: AudioFormat): PcmEncoding? {
        val e = fmt.encoding
        return when (e) {
            PcmEncoding.PCM_16 -> PcmEncoding.PCM_16
            PcmEncoding.PCM_24, PcmEncoding.PCM_32, PcmEncoding.FLOAT, PcmEncoding.UNKNOWN -> null
        }
    }
}
