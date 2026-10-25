package com.alexis.hifiplayer.domain.usecase

import com.alexis.hifiplayer.domain.model.AudioDiagnostics
import com.alexis.hifiplayer.domain.model.AudioPathInfo
import com.alexis.hifiplayer.domain.model.AudioSourceFormat
import com.alexis.hifiplayer.domain.model.BitPerfectStatus

/**
 * Use case to produce an honest AudioDiagnostics object from available domain information.
 */
class GetAudioDiagnosticsUseCase(
    private val detectBitPerfectUseCase: DetectBitPerfectUseCase
) {

    /**
     * Builds diagnostics based on source and path information.
     */
    fun getDiagnostics(
        sourceFormat: AudioSourceFormat,
        pathInfo: AudioPathInfo,
        timestampMs: Long = System.currentTimeMillis()
    ): AudioDiagnostics {
        val status = detectBitPerfectUseCase.evaluate(sourceFormat, pathInfo)

        val evidence = mutableListOf<String>()
        val uncertainties = mutableListOf<String>()
        val reasons = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        evidence.add("Source codec: " + sourceFormat.codec)
        evidence.add("Source isLossless: " + (sourceFormat.isLossless ?: "unknown"))
        pathInfo.requestedOutputFormat?.audioFormat?.let {
            evidence.add("Requested: " + it.sampleRateHz + "Hz/" + it.bitDepth + "-bit/ch" + it.channelCount + "/" + it.encoding)
        }
        pathInfo.actualOutputFormat?.audioFormat?.let {
            evidence.add("Actual: " + it.sampleRateHz + "Hz/" + it.bitDepth + "-bit/ch" + it.channelCount + "/" + it.encoding)
        }
        evidence.add("Device: " + pathInfo.audioDevice.type + " (" + (if (pathInfo.audioDevice.name.isEmpty()) "unknown" else pathInfo.audioDevice.name) + ")")
        evidence.add("Device active: " + (pathInfo.audioDevice.isActive ?: "unknown"))
        evidence.add("Resampling: " + pathInfo.hasResampling)
        evidence.add("DSP: " + pathInfo.hasDsp)
        evidence.add("Downmix: " + pathInfo.hasDownmix)
        evidence.add("Mixer: " + pathInfo.usesMixer)

        uncertainties.addAll(pathInfo.uncertainties)

        when (status) {
            BitPerfectStatus.LOSSLESS -> reasons.add("Content remains lossless but bit-perfect cannot be guaranteed with available evidence")
            BitPerfectStatus.CONVERTED -> reasons.add("Conversion detected in audio path")
            BitPerfectStatus.UNAVAILABLE -> {
                reasons.add("Insufficient information to determine bit-perfect state")
                warnings.add("Diagnosis limited by available APIs")
            }
            else -> {}
        }

        return AudioDiagnostics(
            status = status,
            sourceFormat = sourceFormat,
            decodedFormat = pathInfo.decodedFormat,
            requestedOutputFormat = pathInfo.requestedOutputFormat,
            actualOutputFormat = pathInfo.actualOutputFormat,
            audioDevice = pathInfo.audioDevice,
            deviceCapabilities = pathInfo.deviceCapabilities,
            audioPathInfo = pathInfo,
            evidence = evidence,
            uncertainties = uncertainties,
            reasons = reasons,
            warnings = warnings,
            timestampMs = timestampMs
        )
    }
}
