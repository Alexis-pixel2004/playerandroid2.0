package com.alexis.hifiplayer.domain.usecase

import com.alexis.hifiplayer.domain.model.AudioPathInfo
import com.alexis.hifiplayer.domain.model.AudioSourceFormat
import com.alexis.hifiplayer.domain.model.BitPerfectStatus
import com.alexis.hifiplayer.domain.model.ProcessingState

/**
 * Use case to evaluate bit-perfect status based on available domain information.
 * Conservative by design: requires sufficient evidence before declaring BIT_PERFECT.
 */
class DetectBitPerfectUseCase {

    /**
     * Evaluates bit-perfect status from available audio path/source data.
     * Unknown values are treated as non-verifiable (do not assume OFF).
     */
    fun evaluate(
        sourceFormat: AudioSourceFormat,
        pathInfo: AudioPathInfo
    ): BitPerfectStatus {
        val reasons = mutableListOf<String>()
        val uncertainties = mutableListOf<String>()

        // Check source lossless
        val sourceLossless = sourceFormat.isLossless
        if (sourceLossless == null) {
            uncertainties.add("Source lossless flag not verifiable")
        } else if (!sourceLossless) {
            reasons.add("Source is not lossless")
            return BitPerfectStatus.CONVERTED
        }

        val sourceCodec = sourceFormat.codec
        if (sourceCodec == com.alexis.hifiplayer.domain.model.AudioCodec.UNKNOWN) {
            uncertainties.add("Source codec unknown")
        }

        // Requested/actual output
        val requested = pathInfo.requestedOutputFormat?.audioFormat
        val actual = pathInfo.actualOutputFormat?.audioFormat
        if (requested == null) {
            uncertainties.add("Requested output format not available")
        }
        if (actual == null) {
            uncertainties.add("Actual output format not verifiable")
        }

        // Processing states - never assume UNKNOWN means OFF
        if (pathInfo.hasResampling == ProcessingState.ON) {
            reasons.add("Resampling detected")
            return BitPerfectStatus.CONVERTED
        }
        if (pathInfo.hasResampling == ProcessingState.UNKNOWN) {
            uncertainties.add("Resampling state not verifiable")
        }

        if (pathInfo.hasDsp == ProcessingState.ON) {
            reasons.add("DSP processing detected")
            return BitPerfectStatus.CONVERTED
        }
        if (pathInfo.hasDsp == ProcessingState.UNKNOWN) {
            uncertainties.add("DSP state not verifiable")
        }

        if (pathInfo.hasDownmix == ProcessingState.ON) {
            reasons.add("Downmix detected")
            return BitPerfectStatus.CONVERTED
        }
        if (pathInfo.hasDownmix == ProcessingState.UNKNOWN) {
            uncertainties.add("Downmix state not verifiable")
        }

        if (pathInfo.usesMixer == ProcessingState.ON) {
            reasons.add("Audio mixer active")
            // Do not force CONVERTED; may be lossless but not bit-perfect
        }
        if (pathInfo.usesMixer == ProcessingState.UNKNOWN) {
            uncertainties.add("Mixer state not verifiable")
        }

        if (pathInfo.mixerAttributesPresent == ProcessingState.UNKNOWN) {
            uncertainties.add("Mixer attributes state not verifiable")
        }

        // Format comparison only if both known
        if (requested != null && actual != null) {
            val formatsMatch = (requested.sampleRateHz == actual.sampleRateHz &&
                    requested.bitDepth == actual.bitDepth &&
                    requested.channelCount == actual.channelCount &&
                    requested.encoding == actual.encoding)
            if (!formatsMatch) {
                reasons.add("Output format conversion detected (requested != actual)")
                return BitPerfectStatus.CONVERTED
            }
        } else {
            // Cannot compare reliably
            uncertainties.add("Cannot compare requested vs actual format")
        }

        // Device/capabilities
        val device = pathInfo.audioDevice
        if (device.type == com.alexis.hifiplayer.domain.model.AudioDeviceType.UNKNOWN) {
            uncertainties.add("Audio device type unknown")
        }
        if (device.isActive != true) {
            uncertainties.add("Active audio device not confirmed")
        }

        val caps = pathInfo.deviceCapabilities
        if (caps.capabilityState == com.alexis.hifiplayer.domain.model.CapabilityState.UNKNOWN) {
            uncertainties.add("Device capabilities not fully known")
        }

        // Conservative rule: require no critical uncertainties to declare BIT_PERFECT
        val criticalUncertainties = uncertainties.any {
            it.contains("Actual output format") ||
                    it.contains("requested vs actual") ||
                    it.contains("Resampling state") ||
                    it.contains("DSP state") ||
                    it.contains("Mixer state") ||
                    it.contains("Device capabilities")
        }

        return when {
            reasons.any { it.contains("detected") || it.contains("converted") || it.contains("Resampling") || it.contains("DSP") || it.contains("Downmix") } -> {
                if (reasons.any { it.contains("Resampling") || it.contains("DSP") || it.contains("Downmix") || it.contains("conversion") }) {
                    BitPerfectStatus.CONVERTED
                } else {
                    BitPerfectStatus.LOSSLESS
                }
            }
            criticalUncertainties -> BitPerfectStatus.LOSSLESS
            uncertainties.isNotEmpty() -> BitPerfectStatus.LOSSLESS
            else -> BitPerfectStatus.BIT_PERFECT
        }
    }
}
