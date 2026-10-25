package com.alexis.hifiplayer.audio.diagnostics

import com.alexis.hifiplayer.audio.format.AudioFormatMatchStatus
import com.alexis.hifiplayer.audio.mixer.MixerBehavior
import com.alexis.hifiplayer.domain.model.AudioDevice
import com.alexis.hifiplayer.domain.model.AudioDeviceCapabilities
import com.alexis.hifiplayer.domain.model.AudioDeviceType
import com.alexis.hifiplayer.domain.model.AudioDiagnostics
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

class AudioDiagnosticsCollector(
    private val detectBitPerfectUseCase: DetectBitPerfectUseCase,
    private val getAudioDiagnosticsUseCase: GetAudioDiagnosticsUseCase,
    private val analyzer: AudioPathAnalyzer = AudioPathAnalyzer()
) {

    fun collect(
        sourceFormat: AudioSourceFormat = AudioSourceFormat(),
        requestedOutput: AudioOutputFormat = AudioOutputFormat(),
        pathInfo: AudioPathInfo = AudioPathInfo(),
        configuredFormat: AudioFormat = AudioFormat(),
        audioTrackState: Int? = null,
        audioTrackPlayState: Int? = null,
        audioTrackInitialized: Boolean = false,
        audioTrackBufferSizeBytes: Int? = null,
        audioTrackSampleRate: Int? = null,
        mixerBehavior: MixerBehavior = MixerBehavior.UNKNOWN,
        mixerAttributesAvailable: Boolean = false,
        timestampMs: Long = System.currentTimeMillis()
    ): AudioDiagnostics {
        // Analyze path state
        val pathState = analyzer.analyze(
            sourceFormat = sourceFormat,
            requestedOutput = requestedOutput,
            pathInfo = pathInfo,
            configuredFormat = configuredFormat,
            audioTrackState = audioTrackState,
            audioTrackPlayState = audioTrackPlayState,
            audioTrackInitialized = audioTrackInitialized,
            audioTrackBufferSizeBytes = audioTrackBufferSizeBytes,
            audioTrackSampleRate = audioTrackSampleRate,
            mixerBehavior = mixerBehavior,
            mixerAttributesAvailable = mixerAttributesAvailable
        )

        // Build enriched path info preserving existing fields, adding uncertainties
        val enrichedPathInfo = pathInfo.copy(
            requestedOutputFormat = requestedOutput,
            actualOutputFormat = if (pathState.effectiveFormatKnown) AudioOutputFormat(audioFormat = pathState.effectiveFormat) else pathInfo.actualOutputFormat,
            audioDevice = pathState.audioDevice,
            deviceCapabilities = pathState.deviceCapabilities,
            usesMixer = pathState.usesMixer,
            mixerAttributesPresent = if (pathState.mixerAttributesAvailable) ProcessingState.ON else if (pathState.mixerBehaviorKnown) ProcessingState.UNKNOWN else ProcessingState.UNKNOWN,
            hasResampling = pathState.hasResampling,
            hasDsp = pathState.hasDsp,
            hasDownmix = pathState.hasDownmix,
            uncertainties = buildList {
                addAll(pathInfo.uncertainties)
                addAll(pathState.uncertainties)
            }
        )

        // Get diagnostics from domain use case
        val baseDiag = getAudioDiagnosticsUseCase.getDiagnostics(
            sourceFormat = sourceFormat,
            pathInfo = enrichedPathInfo,
            timestampMs = timestampMs
        )

        // Evidence
        val evidence = buildList {
            addAll(baseDiag.evidence)
            if (audioTrackInitialized) add("AudioTrack initialized: true")
            if (audioTrackState != null) add("AudioTrack state: ")
            if (audioTrackPlayState != null) add("AudioTrack playState: ")
            if (audioTrackBufferSizeBytes != null) add("AudioTrack bufferSizeBytes: ")
            if (audioTrackSampleRate != null) add("AudioTrack sampleRate: ")
            add("Configured format: Hz/ch/")
            add("Effective format known: ")
            add("Mixer behavior known: , behavior: ")
            add("Mixer attributes available: ")
            add("Format match: ")
            if (pathInfo.audioDevice.type == AudioDeviceType.USB_DAC) add("USB DAC detected")
        }

        // Uncertainties
        val uncertainties = buildList {
            addAll(baseDiag.uncertainties)
            addAll(pathState.uncertainties)
            if (!pathState.effectiveFormatKnown) add("Effective output format cannot be verified via public Android APIs")
            if (pathState.mixerBehavior == MixerBehavior.UNKNOWN) add("Mixer behavior not verifiable")
        }

        // Reasons
        val reasons = buildList {
            addAll(baseDiag.reasons)
            addAll(pathState.reasons)
            if (!pathState.effectiveFormatKnown) add("Insufficient public evidence to confirm effective format")
        }

        // Warnings
        val warnings = buildList {
            addAll(baseDiag.warnings)
            addAll(pathState.warnings)
        }

        // Bit-perfect: keep conservative, never infer from single factor
        val domainStatus = detectBitPerfectUseCase.evaluate(sourceFormat, enrichedPathInfo)
        val status = adjustStatusConservatively(domainStatus, pathState)

        return baseDiag.copy(
            status = status,
            audioPathInfo = enrichedPathInfo,
            evidence = evidence.distinct(),
            uncertainties = uncertainties.distinct(),
            reasons = reasons.distinct(),
            warnings = warnings.distinct(),
            timestampMs = timestampMs
        )
    }

    private fun adjustStatusConservatively(status: BitPerfectStatus, state: AudioPathState): BitPerfectStatus {
        // Do not upgrade to BIT_PERFECT if critical unknowns exist
        if (status == BitPerfectStatus.BIT_PERFECT) {
            val criticalUnknowns = !state.effectiveFormatKnown ||
                    state.mixerBehavior == MixerBehavior.UNKNOWN ||
                    state.hasResampling == ProcessingState.UNKNOWN ||
                    state.hasDsp == ProcessingState.UNKNOWN ||
                    state.hasDownmix == ProcessingState.UNKNOWN ||
                    state.usesMixer == ProcessingState.UNKNOWN ||
                    state.deviceCapabilities.capabilityState == CapabilityState.UNKNOWN
            if (criticalUnknowns) {
                return BitPerfectStatus.LOSSLESS
            }
        }
        if (state.hasResampling == ProcessingState.ON ||
            state.hasDsp == ProcessingState.ON ||
            state.hasDownmix == ProcessingState.ON
        ) {
            return BitPerfectStatus.CONVERTED
        }
        return status
    }
}
