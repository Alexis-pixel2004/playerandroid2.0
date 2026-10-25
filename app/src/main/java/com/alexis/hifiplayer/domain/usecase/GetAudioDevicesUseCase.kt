package com.alexis.hifiplayer.domain.usecase

import com.alexis.hifiplayer.domain.model.AudioDevice
import com.alexis.hifiplayer.domain.model.AudioDeviceCapabilities
import com.alexis.hifiplayer.domain.repository.AudioDeviceRepository
import kotlinx.coroutines.flow.Flow

/**
 * Caso de uso para consultar y observar los dispositivos de salida de audio.
 */
class GetAudioDevicesUseCase(
    private val audioDeviceRepository: AudioDeviceRepository
) {

    /**
     * Devuelve todas las salidas enumeradas por el sistema (detectadas). No implica
     * que estén enrutadas.
     */
    suspend fun getAvailableDevices(): List<AudioDevice> {
        return audioDeviceRepository.getAvailableDevices()
    }

    /**
     * Observa las salidas enumeradas por el sistema.
     */
    fun observeAvailableDevices(): Flow<List<AudioDevice>> {
        return audioDeviceRepository.observeAvailableDevices()
    }

    /**
     * Devuelve la salida que Android enrutaría para reproducción multimedia
     * (enrutado según Android), o `null` si no puede determinarse. No implica que
     * esté reproduciendo.
     */
    suspend fun getActiveDevice(): AudioDevice? {
        return audioDeviceRepository.getActiveDevice()
    }

    /**
     * Observa la salida enrutada para reproducción multimedia.
     */
    fun observeActiveDevice(): Flow<AudioDevice?> {
        return audioDeviceRepository.observeActiveDevice()
    }

    /**
     * Capacidades declaradas por Android para [device], o `null` si no pueden
     * determinarse. No son el formato efectivo recibido por el DAC.
     */
    suspend fun getCapabilities(device: AudioDevice): AudioDeviceCapabilities? {
        return audioDeviceRepository.getDeviceCapabilities(device)
    }
}
