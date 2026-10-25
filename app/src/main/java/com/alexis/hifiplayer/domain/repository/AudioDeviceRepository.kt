package com.alexis.hifiplayer.domain.repository

import com.alexis.hifiplayer.domain.model.AudioDevice
import com.alexis.hifiplayer.domain.model.AudioDeviceCapabilities
import kotlinx.coroutines.flow.Flow

/**
 * Contrato de acceso a los dispositivos de salida de audio. Agnóstico de Android.
 *
 * Distinción de conceptos (no confundir):
 * - **Detectado/conectado**: el sistema enumera el dispositivo como salida
 *   disponible ([AudioDevice.isConnected]). No implica que vaya a recibir el audio.
 * - **Enrutado**: Android lo seleccionaría para los atributos de reproducción
 *   multimedia ([AudioDevice.isRouted]). Es la intención de enrutado del sistema;
 *   no es prueba de reproducción efectiva ni del formato que recibe el DAC.
 * - **Reproduciendo**: el dispositivo está recibiendo audio en este momento. Este
 *   contrato no lo expone (requeriría instrumentar la ruta activa).
 *
 * Las capacidades declaradas ([AudioDeviceCapabilities]) describen lo que Android
 * anuncia, no el formato efectivo que recibe el DAC: ese no es verificable mediante
 * las APIs públicas de Android.
 */
interface AudioDeviceRepository {

    /**
     * Devuelve todas las salidas enumeradas por el sistema
     * ([AudioDevice.isConnected] = `true`). No implica que estén enrutadas.
     */
    suspend fun getAvailableDevices(): List<AudioDevice>

    /**
     * Observa las salidas enumeradas por el sistema. Cada emisión es una lista
     * completa (puede ser vacía si no hay salidas detectadas).
     */
    fun observeAvailableDevices(): Flow<List<AudioDevice>>

    /**
     * Devuelve la salida que Android enrutaría para reproducción multimedia
     * ([AudioDevice.isRouted] = `true`), o `null` si no puede determinarse.
     *
     * Conserva el nombre `getActiveDevice` por compatibilidad; su significado es
     * "enrutado según Android", no "reproduciendo".
     */
    suspend fun getActiveDevice(): AudioDevice?

    /**
     * Observa la salida enrutada para reproducción multimedia, o `null` si no
     * puede determinarse.
     */
    fun observeActiveDevice(): Flow<AudioDevice?>

    /**
     * Devuelve las capacidades declaradas por Android para [device], o `null` si
     * no pueden determinarse. No son el formato efectivo recibido por el DAC.
     */
    suspend fun getDeviceCapabilities(device: AudioDevice): AudioDeviceCapabilities?
}
