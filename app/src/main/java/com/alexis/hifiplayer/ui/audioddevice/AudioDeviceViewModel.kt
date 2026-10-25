package com.alexis.hifiplayer.ui.audioddevice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alexis.hifiplayer.domain.model.AudioDevice
import com.alexis.hifiplayer.domain.model.AudioDeviceCapabilities
import com.alexis.hifiplayer.domain.model.AudioDeviceType
import com.alexis.hifiplayer.domain.model.PcmEncoding
import com.alexis.hifiplayer.domain.usecase.GetAudioDevicesUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * Estado de interfaz del dispositivo de salida de audio.
 *
 * Representa únicamente lo que puede conocerse con las APIs públicas:
 * - [availableDevices]: salidas enumeradas por el sistema (detectadas).
 * - [routedDevice]: salida que Android enrutaría para audio multimedia. Es
 *   "enrutado según Android", no prueba de reproducción efectiva.
 * - [capabilities]: capacidades **declaradas** por Android para [routedDevice].
 *   No son el formato efectivo recibido por el DAC.
 * - [uncertainties]: avisos de datos no declarados o no verificables.
 * - [errorMessage]: error de consulta, si lo hubo. Nunca convive con datos
 *   obsoletos: al fallar se descarta el estado anterior.
 *
 * Los valores desconocidos se representan con `null`/listas vacías, nunca con `0`.
 */
data class AudioDeviceUiState(
    val isLoading: Boolean = true,
    val availableDevices: List<AudioDevice> = emptyList(),
    val routedDevice: AudioDevice? = null,
    val capabilities: AudioDeviceCapabilities? = null,
    val uncertainties: List<String> = emptyList(),
    val errorMessage: String? = null
) {
    /** `true` cuando hay una salida enrutada confirmada. */
    val hasRoutedDevice: Boolean get() = routedDevice != null
}

/**
 * Constructor puro del estado de interfaz. Sin dependencias de Android: verificable
 * con JUnit. No inventa datos: si no hay dispositivo enrutado, lo refleja y añade
 * la incertidumbre correspondiente.
 */
internal fun buildAudioDeviceUiState(
    availableDevices: List<AudioDevice>,
    routedDevice: AudioDevice?,
    routedCapabilities: AudioDeviceCapabilities?
): AudioDeviceUiState {
    val uncertainties = mutableListOf<String>()
    if (routedDevice == null) {
        uncertainties.add(
            "Android no confirma un dispositivo de salida enrutado para reproducción multimedia"
        )
    } else if (routedCapabilities == null) {
        uncertainties.add(
            "No se pudieron consultar las capacidades declaradas del dispositivo enrutado"
        )
    }
    if (routedCapabilities != null) {
        uncertainties.addAll(routedCapabilities.uncertaintyFlags)
    }
    return AudioDeviceUiState(
        isLoading = false,
        availableDevices = availableDevices,
        routedDevice = routedDevice,
        capabilities = routedCapabilities,
        uncertainties = uncertainties.distinct(),
        errorMessage = null
    )
}

/**
 * ViewModel del dispositivo de salida de audio, compartido por Ajustes y
 * "Ahora suena" a través de `AppRoot`.
 *
 * El estado se recalcula al arrancar y cada vez que cambia la lista de dispositivos
 * ([deviceChanges]). No hay sondeo permanente: el flujo se detiene cuando nadie
 * observa y se reactiva al volver a primer plano (recogida ligada al ciclo de vida),
 * momento en el que se vuelve a consultar. Ante un error, se emite un estado de error
 * **sin** conservar los datos anteriores.
 */
class AudioDeviceViewModel(
    private val getAudioDevices: GetAudioDevicesUseCase,
    deviceChanges: Flow<Unit>
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<AudioDeviceUiState> = deviceChanges
        .onStart { emit(Unit) }
        // mapLatest cancela una consulta anterior en curso si llega un cambio nuevo,
        // evitando aplicar datos obsoletos.
        .mapLatest { safeReadState() }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = AudioDeviceUiState()
        )

    private suspend fun safeReadState(): AudioDeviceUiState =
        try {
            readState()
        } catch (e: CancellationException) {
            // La cancelación (cambio nuevo o parada de la colección) no es un error.
            throw e
        } catch (e: Exception) {
            // Al fallar no se conserva el estado anterior: se informa sin datos viejos.
            AudioDeviceUiState(isLoading = false, errorMessage = ERROR_MESSAGE)
        }

    private suspend fun readState(): AudioDeviceUiState {
        val available = getAudioDevices.getAvailableDevices()
        val routed = getAudioDevices.getActiveDevice()
        val capabilities = if (routed != null) getAudioDevices.getCapabilities(routed) else null
        return buildAudioDeviceUiState(available, routed, capabilities)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val ERROR_MESSAGE = "No se pudo consultar el dispositivo de salida de audio"
    }
}

/**
 * Factoría tipada para construir [AudioDeviceViewModel]. Sigue el mismo patrón que
 * `SettingsViewModelFactory`.
 */
class AudioDeviceViewModelFactory(
    private val getAudioDevices: GetAudioDevicesUseCase,
    private val deviceChanges: Flow<Unit>
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AudioDeviceViewModel::class.java)) {
            return AudioDeviceViewModel(getAudioDevices, deviceChanges) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

/** Nombre visible de la salida: el del sistema, o el tipo si no tiene nombre. */
internal fun AudioDevice.displayName(): String =
    name.ifBlank { type.displayLabel() }

internal fun AudioDeviceType.displayLabel(): String = when (this) {
    AudioDeviceType.PHONE_SPEAKER -> "Altavoz del teléfono"
    AudioDeviceType.WIRED_HEADSET -> "Auriculares con cable / línea"
    AudioDeviceType.BLUETOOTH_HEADPHONES -> "Bluetooth"
    AudioDeviceType.USB_DAC -> "USB DAC"
    AudioDeviceType.HDMI -> "HDMI"
    AudioDeviceType.UNKNOWN -> "Tipo desconocido"
}

internal fun PcmEncoding.displayLabel(): String = when (this) {
    PcmEncoding.PCM_16 -> "PCM 16 bits"
    PcmEncoding.PCM_24 -> "PCM 24 bits"
    PcmEncoding.PCM_32 -> "PCM 32 bits"
    PcmEncoding.FLOAT -> "PCM en coma flotante"
    PcmEncoding.UNKNOWN -> "Desconocida"
}

/** Formatea frecuencias de muestreo declaradas; vacío ⇒ "no declaradas". */
internal fun formatSampleRates(rates: List<Int>): String =
    if (rates.isEmpty()) {
        "No declaradas por Android"
    } else {
        rates.sorted().joinToString(", ") { "$it Hz" }
    }

/** Formatea el número de canales declarado; vacío ⇒ "no declarado". */
internal fun formatChannelCounts(counts: List<Int>): String =
    if (counts.isEmpty()) {
        "No declarado por Android"
    } else {
        counts.sorted().joinToString(", ")
    }

/** Formatea las codificaciones declaradas; vacío ⇒ "no declaradas". */
internal fun formatEncodings(encodings: List<PcmEncoding>): String =
    if (encodings.isEmpty()) {
        "No declaradas por Android"
    } else {
        encodings.joinToString(", ") { it.displayLabel() }
    }
