package com.alexis.hifiplayer.data.device

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import com.alexis.hifiplayer.domain.model.AudioDevice
import com.alexis.hifiplayer.domain.model.AudioDeviceCapabilities
import com.alexis.hifiplayer.domain.model.AudioDeviceType
import com.alexis.hifiplayer.domain.model.CapabilityState
import com.alexis.hifiplayer.domain.model.PcmEncoding
import com.alexis.hifiplayer.domain.repository.AudioDeviceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class AndroidAudioDeviceProvider(
    private val context: Context
) : AudioDeviceRepository {

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Atributos de reproducción multimedia: los mismos atributos con los que el
     * motor reproduce audio con `USAGE_MEDIA`/`CONTENT_TYPE_MUSIC`. Se usan para
     * preguntar a Android qué dispositivo enrutaría para ese caso de uso, sin
     * influir en la ruta de reproducción real.
     */
    private val mediaAudioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    override suspend fun getAvailableDevices(): List<AudioDevice> {
        val devices: Array<AudioDeviceInfo> = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val routedIds: Set<Int>? = routedDeviceIdsOrNull()
        val list = mutableListOf<AudioDevice>()
        for (device in devices) {
            // `null` cuando el enrutado no puede confirmarse; `true`/`false` cuando sí.
            val isRouted: Boolean? = routedIds?.contains(device.id)
            val mapped = mapAudioDeviceInfo(device, routed = isRouted)
            if (mapped != null) list.add(mapped)
        }
        return list
    }


    override fun observeAvailableDevices(): Flow<List<AudioDevice>> = flow {
        emit(getAvailableDevices())
    }

    override suspend fun getActiveDevice(): AudioDevice? = computeRoutedDevice()

    override fun observeActiveDevice(): Flow<AudioDevice?> = flow {
        emit(computeRoutedDevice())
    }

    override suspend fun getDeviceCapabilities(device: AudioDevice): AudioDeviceCapabilities? {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        var target: AudioDeviceInfo? = null
        for (i in devices.indices) {
            val d = devices[i]
            if (d.isSink && matchesDevice(d, device)) {
                target = d
                break
            }
        }
        if (target == null) {
            return AudioDeviceCapabilities(
                supportedSampleRates = emptyList(),
                supportedEncodings = emptyList(),
                supportedChannelCounts = emptyList(),
                maxBitDepth = null,
                supportsBitPerfect = null,
                isUsb = device.isUsb,
                capabilityState = CapabilityState.UNKNOWN,
                uncertaintyFlags = listOf(
                    "Dispositivo no localizado vía AudioDeviceInfo; capacidades no declaradas por Android"
                )
            )
        }
        return capabilitiesFor(target, device)
    }

    /**
     * Salidas que Android usaría para enrutar los atributos multimedia, o `null` si
     * no puede determinarse (p. ej. si la consulta falla).
     *
     * Usa la API pública [AudioManager.getAudioDevicesForAttributes] (añadida en
     * API 33, disponible con `minSdk` 34): no es API oculta ni requiere permisos.
     * Refleja la intención de enrutado del sistema, no la reproducción efectiva ni
     * el formato que finalmente recibe el DAC.
     */
    private fun routedForMediaAttributesOrNull(): List<AudioDeviceInfo>? =
        try {
            audioManager.getAudioDevicesForAttributes(mediaAudioAttributes)
        } catch (e: Exception) {
            // Si en tiempo de ejecución la consulta no está disponible, se degrada a
            // "no confirmado" en lugar de presentar un enrutado dudoso como real.
            null
        }

    /**
     * Ids de las salidas enrutadas para atributos multimedia, o `null` si no puede
     * confirmarse. Alimenta el estado [AudioDevice.isRouted].
     */
    private fun routedDeviceIdsOrNull(): Set<Int>? {
        val routed = routedForMediaAttributesOrNull() ?: return null
        val ids = mutableSetOf<Int>()
        for (device in routed) {
            ids.add(device.id)
        }
        return ids
    }

    /**
     * Salida que Android enrutaría para audio multimedia, o `null` si no puede
     * confirmarse. No usa [AudioDeviceInfo.isSink] como señal de enrutado: la ruta
     * la indica [AudioManager.getAudioDevicesForAttributes].
     */
    private fun computeRoutedDevice(): AudioDevice? {
        val routed = routedForMediaAttributesOrNull() ?: return null
        for (device in routed) {
            val mapped = mapAudioDeviceInfo(device, routed = true)
            if (mapped != null) return mapped
        }
        return null
    }

    /**
     * Empareja una entrada de `AudioDeviceInfo` con un [AudioDevice] de dominio.
     * Prioriza el identificador de Android (fiable cuando ambos lo conocen) y
     * recurre al tipo/nombre solo como último recurso.
     */
    private fun matchesDevice(info: AudioDeviceInfo, device: AudioDevice): Boolean {
        val deviceId = device.id?.toIntOrNull()
        if (deviceId != null) {
            return info.id == deviceId
        }
        val typeMatch = mapDeviceType(info.type) == device.type
        val nameMatch = device.name.isNotEmpty() &&
            info.productName?.toString().equals(device.name, ignoreCase = true)
        return typeMatch || nameMatch
    }

    private fun capabilitiesFor(
        info: AudioDeviceInfo,
        device: AudioDevice
    ): AudioDeviceCapabilities {
        val sampleRates = info.sampleRates?.toList() ?: emptyList()
        val channelCounts = info.channelCounts?.toList() ?: emptyList()

        val encodings = mutableListOf<PcmEncoding>()
        var unmappedEncodings = 0
        val rawEncodings = info.encodings
        if (rawEncodings != null) {
            for (raw in rawEncodings) {
                val mapped = mapAndroidEncodingToPcmEncoding(raw)
                if (mapped == null) {
                    unmappedEncodings++
                } else if (mapped !in encodings) {
                    encodings.add(mapped)
                }
            }
        }

        val uncertainty = mutableListOf<String>()
        if (sampleRates.isEmpty()) {
            uncertainty.add("Frecuencias de muestreo no declaradas por Android")
        }
        if (channelCounts.isEmpty()) {
            uncertainty.add("Número de canales no declarado por Android")
        }
        if (encodings.isEmpty()) {
            uncertainty.add("Codificaciones no declaradas por Android")
        }
        if (unmappedEncodings > 0) {
            uncertainty.add("Hay codificaciones declaradas no representables en el modelo de dominio")
        }

        val known = sampleRates.isNotEmpty() || channelCounts.isNotEmpty() || encodings.isNotEmpty()

        return AudioDeviceCapabilities(
            supportedSampleRates = sampleRates,
            supportedEncodings = encodings,
            supportedChannelCounts = channelCounts,
            // Sin base fiable para inferir la profundidad de bits: se deja desconocida.
            maxBitDepth = null,
            supportsBitPerfect = null,
            isUsb = device.isUsb,
            capabilityState = if (known) CapabilityState.KNOWN else CapabilityState.UNKNOWN,
            uncertaintyFlags = uncertainty
        )
    }

    private fun mapAudioDeviceInfo(info: AudioDeviceInfo, routed: Boolean?): AudioDevice? {
        if (!info.isSink) return null
        val type = mapDeviceType(info.type)
        val isUsb = isUsbType(info.type)
        return AudioDevice(
            // Identificador de Android: permite un emparejamiento fiable posterior.
            id = info.id.toString(),
            name = info.productName?.toString() ?: "",
            type = type,
            isConnected = true,
            // No se conoce si el dispositivo está reproduciendo; `isSink` no lo indica.
            isActive = null,
            isDefault = null,
            isUsb = isUsb,
            isRouted = routed
        )
    }

    private fun isUsbType(type: Int): Boolean = type == AudioDeviceInfo.TYPE_USB_DEVICE ||
        type == AudioDeviceInfo.TYPE_USB_HEADSET ||
        type == AudioDeviceInfo.TYPE_USB_ACCESSORY

    private fun mapDeviceType(type: Int): AudioDeviceType = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioDeviceType.PHONE_SPEAKER
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL -> AudioDeviceType.WIRED_HEADSET
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_HEARING_AID -> AudioDeviceType.BLUETOOTH_HEADPHONES
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> AudioDeviceType.USB_DAC
        AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC -> AudioDeviceType.HDMI
        else -> AudioDeviceType.UNKNOWN
    }
}

/**
 * Traduce una codificación declarada por Android (`AudioFormat.ENCODING_*`) al
 * [PcmEncoding] del dominio.
 *
 * Devuelve `null` cuando el modelo no puede representarla: nunca se inventa una
 * equivalencia. Se usan constantes con nombre (nunca literales numéricos) para
 * evitar confundir `ENCODING_DEFAULT` (1) con `ENCODING_PCM_16BIT` (2).
 *
 * Es una función pura sin dependencias de estado de Android: las constantes
 * `ENCODING_*` se inlinean en compilación, por lo que es verificable con JUnit.
 */
internal fun mapAndroidEncodingToPcmEncoding(encoding: Int): PcmEncoding? = when (encoding) {
    AudioFormat.ENCODING_PCM_16BIT -> PcmEncoding.PCM_16
    AudioFormat.ENCODING_PCM_24BIT_PACKED -> PcmEncoding.PCM_24
    AudioFormat.ENCODING_PCM_32BIT -> PcmEncoding.PCM_32
    AudioFormat.ENCODING_PCM_FLOAT -> PcmEncoding.FLOAT
    else -> null
}
