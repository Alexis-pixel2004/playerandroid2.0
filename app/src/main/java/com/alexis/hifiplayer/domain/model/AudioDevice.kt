package com.alexis.hifiplayer.domain.model

/**
 * Represents an audio output device (agnostic from Android AudioDeviceInfo).
 *
 * Semántica de los indicadores (conceptos distintos, no confundir):
 * - [isConnected]: el sistema enumera el dispositivo como salida disponible
 *   ("detectado"). No implica que vaya a recibir el audio.
 * - [isRouted]: Android lo seleccionaría para los atributos de reproducción
 *   multimedia ("enrutado según Android"). Es la intención de enrutado del sistema,
 *   no prueba de que esté reproduciendo ni del formato que recibe.
 * - [isActive]: indicador con su semántica anterior (dispositivo activo). No
 *   representa el enrutado; para eso está [isRouted]. `null` = desconocido.
 * - [isDefault]: dispositivo predeterminado del sistema, o `null` si no es verificable.
 *
 * Un valor que no puede conocerse queda en `null`: nunca se inventa ni se usa `0`.
 */
data class AudioDevice(
    val id: String? = null,
    val name: String = "",
    val type: AudioDeviceType = AudioDeviceType.UNKNOWN,
    val isConnected: Boolean? = null,
    val isActive: Boolean? = null,
    val isDefault: Boolean? = null,
    val isUsb: Boolean? = null,
    /**
     * `true` si Android lo enrutaría para audio multimedia (atributos de uso
     * `MEDIA`); `false` si se conoce que no; `null` si no es verificable.
     * No implica reproducción efectiva ni el formato que recibe el DAC.
     */
    val isRouted: Boolean? = null
)
