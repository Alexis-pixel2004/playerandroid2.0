package com.alexis.hifiplayer.audio

import com.alexis.hifiplayer.audio.diagnostics.AudioDiagnosticsCollector
import com.alexis.hifiplayer.audio.format.AudioFormatResolution
import com.alexis.hifiplayer.audio.format.AudioFormatResolutionStatus
import com.alexis.hifiplayer.audio.format.AudioFormatResolver
import com.alexis.hifiplayer.audio.mixer.AudioMixerAttributesProvider
import com.alexis.hifiplayer.audio.mixer.MixerBehavior
import com.alexis.hifiplayer.audio.output.AudioTrackCreationStatus
import com.alexis.hifiplayer.audio.output.AudioTrackOutputProvider
import com.alexis.hifiplayer.domain.model.AudioDeviceCapabilities
import com.alexis.hifiplayer.domain.model.AudioDiagnostics
import com.alexis.hifiplayer.domain.model.AudioFormat
import com.alexis.hifiplayer.domain.model.AudioOutputFormat
import com.alexis.hifiplayer.domain.model.AudioPathInfo
import com.alexis.hifiplayer.domain.model.AudioSourceFormat

class AudioEngine(
    private val audioTrackOutputProvider: AudioTrackOutputProvider,
    private val audioDiagnosticsCollector: AudioDiagnosticsCollector,
    private val mixerAttributesProvider: AudioMixerAttributesProvider,
    private val formatResolver: AudioFormatResolver = AudioFormatResolver()
) {
    @Volatile
    private var _state: AudioEngineState = AudioEngineState.IDLE
    val state: AudioEngineState get() = _state

    private var currentSourceFormat: AudioSourceFormat = AudioSourceFormat()
    private var currentOutputFormat: AudioOutputFormat = AudioOutputFormat()
    private var currentConfiguredFormat: AudioFormat = AudioFormat()
    private var currentResolution: AudioFormatResolution? = null
    private var currentError: String? = null
    private val currentWarnings: MutableList<String> = mutableListOf()

    fun prepare(sourceFormat: AudioSourceFormat, capabilities: AudioDeviceCapabilities) {
        if (_state != AudioEngineState.IDLE && _state != AudioEngineState.RELEASED && _state != AudioEngineState.STOPPED) {
            stop()
        }
        currentError = null
        currentWarnings.clear()
        currentSourceFormat = sourceFormat
        currentOutputFormat = AudioOutputFormat()
        currentConfiguredFormat = AudioFormat()
        currentResolution = null

        val resolution = formatResolver.resolve(sourceFormat, capabilities)
        currentResolution = resolution
        currentWarnings.addAll(resolution.warnings)
        currentOutputFormat = resolution.requested
        currentConfiguredFormat = resolution.configured

        when (resolution.status) {
            AudioFormatResolutionStatus.EXACT,
            AudioFormatResolutionStatus.PARTIAL -> {
                val result = audioTrackOutputProvider.createOutput(resolution.requested)
                when (result.status) {
                    AudioTrackCreationStatus.SUCCESS -> {
                        currentConfiguredFormat = audioTrackOutputProvider.getConfiguredFormat()
                        currentWarnings.addAll(result.warnings)
                        _state = AudioEngineState.PREPARED
                    }
                    else -> {
                        currentError = result.reason ?: result.status.name
                        currentWarnings.addAll(result.warnings)
                        _state = AudioEngineState.IDLE
                        audioTrackOutputProvider.release()
                    }
                }
            }
            AudioFormatResolutionStatus.INCOMPATIBLE,
            AudioFormatResolutionStatus.UNSUPPORTED,
            AudioFormatResolutionStatus.UNKNOWN -> {
                currentError = "Format resolution: ${resolution.status}"
                currentWarnings.addAll(resolution.reasons)
                audioTrackOutputProvider.release()
                _state = AudioEngineState.IDLE
            }
        }
    }

    fun prepare(sourceFormat: AudioSourceFormat, outputFormat: AudioOutputFormat) {
        if (_state != AudioEngineState.IDLE && _state != AudioEngineState.RELEASED && _state != AudioEngineState.STOPPED) {
            stop()
        }
        currentError = null
        currentWarnings.clear()
        currentSourceFormat = sourceFormat
        currentOutputFormat = outputFormat
        currentConfiguredFormat = AudioFormat()
        currentResolution = null

        val result = audioTrackOutputProvider.createOutput(outputFormat)
        when (result.status) {
            AudioTrackCreationStatus.SUCCESS -> {
                currentConfiguredFormat = audioTrackOutputProvider.getConfiguredFormat()
                currentWarnings.addAll(result.warnings)
                _state = AudioEngineState.PREPARED
            }
            else -> {
                currentError = result.reason ?: result.status.name
                currentWarnings.addAll(result.warnings)
                _state = AudioEngineState.IDLE
                audioTrackOutputProvider.release()
            }
        }
    }

    fun start() {
        if (_state == AudioEngineState.PREPARED || _state == AudioEngineState.PAUSED) {
            audioTrackOutputProvider.start()
            if (audioTrackOutputProvider.getState() == android.media.AudioTrack.STATE_INITIALIZED) {
                _state = AudioEngineState.PLAYING
            }
        }
    }

    fun pause() {
        if (_state == AudioEngineState.PLAYING) {
            audioTrackOutputProvider.pause()
            _state = AudioEngineState.PAUSED
        }
    }

    fun stop() {
        audioTrackOutputProvider.stop()
        _state = AudioEngineState.STOPPED
    }

    fun release() {
        audioTrackOutputProvider.release()
        currentError = null
        currentWarnings.clear()
        currentConfiguredFormat = AudioFormat()
        currentOutputFormat = AudioOutputFormat()
        currentSourceFormat = AudioSourceFormat()
        currentResolution = null
        _state = AudioEngineState.RELEASED
    }

    fun getConfiguredFormat(): AudioFormat = currentConfiguredFormat
    fun getOutputFormat(): AudioOutputFormat = currentOutputFormat
    fun getSourceFormat(): AudioSourceFormat = currentSourceFormat
    fun getResolution(): AudioFormatResolution? = currentResolution

    fun getStatus(): AudioEngineStatus {
        val atState = audioTrackOutputProvider.getState()
        val atPlay = audioTrackOutputProvider.getPlayState()
        val atInit = (atState == android.media.AudioTrack.STATE_INITIALIZED)
        val buf = audioTrackOutputProvider.getLastBufferSizeBytes()
        val sr = audioTrackOutputProvider.getSampleRate()
        return AudioEngineStatus(
            state = _state,
            audioTrackInitialized = atInit,
            audioTrackPlayState = atPlay,
            audioTrackState = atState,
            bufferSizeBytes = buf,
            sampleRate = sr,
            configuredFormat = currentConfiguredFormat,
            outputFormat = currentOutputFormat,
            sourceFormat = currentSourceFormat,
            resolution = currentResolution,
            error = currentError,
            warnings = currentWarnings.toList()
        )
    }

    fun getDiagnostics(pathInfo: AudioPathInfo): AudioDiagnostics {
        val atState = audioTrackOutputProvider.getState()
        val atPlay = audioTrackOutputProvider.getPlayState()
        val atInit = (atState == android.media.AudioTrack.STATE_INITIALIZED)
        val buf = audioTrackOutputProvider.getLastBufferSizeBytes()
        val sr = audioTrackOutputProvider.getSampleRate()
        return audioDiagnosticsCollector.collect(
            sourceFormat = currentSourceFormat,
            requestedOutput = currentOutputFormat,
            pathInfo = pathInfo,
            configuredFormat = currentConfiguredFormat,
            audioTrackState = atState,
            audioTrackPlayState = atPlay,
            audioTrackInitialized = atInit,
            audioTrackBufferSizeBytes = if (buf > 0) buf else null,
            audioTrackSampleRate = sr,
            mixerBehavior = MixerBehavior.UNKNOWN,
            mixerAttributesAvailable = false
        )
    }
}

