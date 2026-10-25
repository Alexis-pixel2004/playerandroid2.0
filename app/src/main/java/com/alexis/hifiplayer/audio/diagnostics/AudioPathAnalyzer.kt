package com.alexis.hifiplayer.audio.diagnostics

import com.alexis.hifiplayer.audio.format.AudioFormatMatcher
import com.alexis.hifiplayer.audio.format.AudioFormatMatchStatus
import com.alexis.hifiplayer.audio.mixer.MixerBehavior
import com.alexis.hifiplayer.domain.model.AudioFormat
import com.alexis.hifiplayer.domain.model.AudioOutputFormat
import com.alexis.hifiplayer.domain.model.AudioPathInfo
import com.alexis.hifiplayer.domain.model.AudioSourceFormat
import com.alexis.hifiplayer.domain.model.PcmEncoding
import com.alexis.hifiplayer.domain.model.ProcessingState

class AudioPathAnalyzer(
    private val formatMatcher: AudioFormatMatcher = AudioFormatMatcher()
) {

    fun analyze(
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
        mixerAttributesAvailable: Boolean = false
    ): AudioPathState {
        val uncertainties = mutableListOf<String>()
        val reasons = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Device/capabilities
        val device = pathInfo.audioDevice
        val caps = pathInfo.deviceCapabilities

        if (device.type == com.alexis.hifiplayer.domain.model.AudioDeviceType.UNKNOWN) {
            uncertainties.add("Active audio device type unknown")
        }
        if (device.isActive != true) {
            uncertainties.add("Active audio device not confirmed")
        }
        if (caps.capabilityState == com.alexis.hifiplayer.domain.model.CapabilityState.UNKNOWN) {
            uncertainties.add("Device capabilities not fully known (declared only when exposed)")
        }
        uncertainties.addAll(caps.uncertaintyFlags)
        uncertainties.addAll(pathInfo.uncertainties)

        // Format match
        val matchRes = formatMatcher.match(sourceFormat.audioFormat, caps)
        when (matchRes.status) {
            AudioFormatMatchStatus.EXACT_MATCH -> {
                // EXACT_MATCH means declared capabilities match known source fields; not proof of bit-perfect
                reasons.add("Declared capabilities show potential exact match for known fields")
            }
            AudioFormatMatchStatus.PARTIAL_MATCH -> reasons.add("Declared capabilities partially match")
            AudioFormatMatchStatus.NO_MATCH -> reasons.add("Declared capabilities do not match known source fields")
            AudioFormatMatchStatus.UNKNOWN -> uncertainties.add("Format match cannot be determined from available information")
        }
        reasons.addAll(matchRes.reasons)
        uncertainties.addAll(matchRes.uncertainties)

        // Effective format - cannot be verified from public APIs without playback info
        val effectiveKnown = false
        if (!effectiveKnown) {
            uncertainties.add("Effective output format of the audio path cannot be verified via public Android APIs at this stage")
            reasons.add("Insufficient public evidence to confirm effective format")
        }

        if (mixerBehavior == MixerBehavior.UNKNOWN) {
            uncertainties.add("Mixer behavior not verifiable via public APIs available")
        } else if (mixerBehavior == MixerBehavior.BIT_PERFECT) {
            // Report as potential behavior, do NOT infer end-to-end bit-perfect
            warnings.add("Mixer behavior reported as BIT_PERFECT by Android API context; does not imply end-to-end bit-perfect")
        }

        if (!mixerAttributesAvailable) {
            uncertainties.add("AudioMixerAttributes not applied/available for verification in current configuration")
        }

        // Processing states preserved as-is (conservative)
        val usesMixer = pathInfo.usesMixer
        val hasResampling = pathInfo.hasResampling
        val hasDsp = pathInfo.hasDsp
        val hasDownmix = pathInfo.hasDownmix

        return AudioPathState(
            sourceFormatPresent = true,
            requestedOutputFormat = requestedOutput,
            configuredFormat = configuredFormat,
            effectiveFormat = AudioFormat(sampleRateHz = 0, bitDepth = 0, channelCount = 0, encoding = PcmEncoding.UNKNOWN),
            effectiveFormatKnown = false,
            audioDevice = device,
            deviceCapabilities = caps,
            audioTrackInitialized = audioTrackInitialized,
            audioTrackPlayState = audioTrackPlayState,
            audioTrackState = audioTrackState,
            audioTrackBufferSizeBytes = audioTrackBufferSizeBytes,
            audioTrackSampleRate = audioTrackSampleRate,
            mixerBehaviorKnown = (mixerBehavior != MixerBehavior.UNKNOWN),
            mixerBehavior = mixerBehavior,
            mixerAttributesAvailable = mixerAttributesAvailable,
            hasResampling = hasResampling,
            hasDsp = hasDsp,
            hasDownmix = hasDownmix,
            usesMixer = usesMixer,
            formatMatchStatus = matchRes.status,
            reasons = reasons,
            uncertainties = uncertainties,
            warnings = warnings
        )
    }
}
