package com.alexis.hifiplayer.ui.audioddevice

import com.alexis.hifiplayer.domain.model.AudioCodec
import com.alexis.hifiplayer.domain.model.AudioDevice
import com.alexis.hifiplayer.domain.model.AudioDeviceCapabilities
import com.alexis.hifiplayer.domain.model.AudioPathInfo
import com.alexis.hifiplayer.domain.model.AudioSourceFormat
import com.alexis.hifiplayer.domain.model.AudioTrackConfig
import com.alexis.hifiplayer.domain.model.BitPerfectStatus
import com.alexis.hifiplayer.domain.model.CapabilityState
import com.alexis.hifiplayer.domain.model.DecoderInputFormat
import com.alexis.hifiplayer.domain.model.SelectedTrackFormat
import com.alexis.hifiplayer.domain.model.Song
import com.alexis.hifiplayer.domain.usecase.DetectBitPerfectUseCase

/**
 * Diagnóstico de la ruta de audio (Etapa 1), compartido por Ajustes y "Ahora suena".
 *
 * Distingue explícitamente:
 * - formato de **origen** ([sourceCodec], [sourceSampleRateHz], [sourceBitDepth],
 *   [sourceChannelCount], [sourceBitRate]): derivan de los metadatos del archivo
 *   ([Song]); los desconocidos se conservan como `null`;
 * - formato **seleccionado por el reproductor** ([selectedFormat], Etapa 2a): el stream
 *   que ExoPlayer declara haber seleccionado; describe el contenedor/archivo, no el PCM
 *   decodificado ni el formato del DAC;
 * - **estado** de bit-perfect ([bitPerfectStatus]) con sus [reasons]. Es conservador:
 *   como "No confirmado" salvo evidencia positiva de incompatibilidad.
 *
 * No guarda el formato solicitado para la salida ni el efectivo del DAC: no existen
 * datos que los respalden. La UI los etiqueta como no disponibles / no verificables en
 * lugar de inferirlos.
 *
 * Se crea en lugar de reutilizar `AudioSourceFormat`/`AudioFormat` porque sus campos
 * numéricos son `Int` no nulos con 0 por defecto: eso convertiría "desconocido" en 0,
 * incumpliendo la regla de no inventar valores.
 */
data class AudioPathDiagnostics(
    val hasSource: Boolean = false,
    val sourceCodec: AudioCodec = AudioCodec.UNKNOWN,
    val sourceIsLossless: Boolean? = null,
    val sourceSampleRateHz: Int? = null,
    val sourceBitDepth: Int? = null,
    val sourceChannelCount: Int? = null,
    val sourceBitRate: Long? = null,
    val selectedFormat: SelectedTrackFormat? = null,
    val decoderInputFormat: DecoderInputFormat? = null,
    val audioTrackConfig: AudioTrackConfig? = null,
    val bitPerfectStatus: BitPerfectStatus = BitPerfectStatus.UNAVAILABLE,
    val reasons: List<String> = emptyList()
)

/**
 * Constructor puro del diagnóstico (sin Android: verificable con JUnit).
 *
 * Solo clasifica el bit-perfect a partir de datos ya disponibles: origen ([song]),
 * dispositivo enrutado y capacidades **declaradas** ([deviceState]). No mide ni afirma
 * el formato PCM real recibido por el DAC.
 */
internal fun buildAudioPathDiagnostics(
    song: Song?,
    deviceState: AudioDeviceUiState,
    selectedFormat: SelectedTrackFormat? = null,
    decoderInputFormat: DecoderInputFormat? = null,
    audioTrackConfig: AudioTrackConfig? = null,
    detectBitPerfect: DetectBitPerfectUseCase = DetectBitPerfectUseCase()
): AudioPathDiagnostics {
    val routed = deviceState.routedDevice
    val capabilities = deviceState.capabilities
    val sourceCodec = song?.codec ?: AudioCodec.UNKNOWN
    val sourceLossless = song?.codec?.isLossless

    // Modelo de dominio solo para el clasificador: este no lee los campos numéricos de
    // AudioFormat, que por tanto no se exponen (no se convierten desconocidos en 0).
    val sourceFormat = AudioSourceFormat(
        codec = sourceCodec,
        isLossless = sourceLossless,
        bitRate = song?.bitRate
    )
    val pathInfo = AudioPathInfo(
        sourceFormat = sourceFormat,
        audioDevice = routed ?: AudioDevice(),
        deviceCapabilities = capabilities ?: AudioDeviceCapabilities()
    )
    val status = detectBitPerfect.evaluate(sourceFormat, pathInfo)

    return AudioPathDiagnostics(
        hasSource = song != null,
        sourceCodec = sourceCodec,
        sourceIsLossless = sourceLossless,
        sourceSampleRateHz = song?.sampleRateHz,
        sourceBitDepth = song?.bitDepth,
        sourceChannelCount = song?.channelCount,
        sourceBitRate = song?.bitRate,
        selectedFormat = selectedFormat,
        decoderInputFormat = decoderInputFormat,
        audioTrackConfig = audioTrackConfig,
        bitPerfectStatus = status,
        reasons = bitPerfectReasons(sourceLossless, routed, capabilities)
    )
}

/**
 * Motivos (en español) por los que el estado no es bit-perfect confirmado. Nunca
 * declaran una medición del DAC: describen datos conocidos o su ausencia.
 */
private fun bitPerfectReasons(
    sourceLossless: Boolean?,
    routed: AudioDevice?,
    capabilities: AudioDeviceCapabilities?
): List<String> {
    val reasons = mutableListOf<String>()
    when (sourceLossless) {
        false -> reasons.add("El códec de origen tiene pérdidas (lossy).")
        null -> reasons.add("No se pudo determinar si el códec de origen es sin pérdidas.")
        true -> reasons.add(
            "El origen es sin pérdidas, pero eso no confirma el recorrido completo de la señal."
        )
    }
    if (routed == null) {
        reasons.add("Android no confirma un dispositivo de salida enrutado.")
    } else if (routed.isRouted != true) {
        reasons.add("Android no confirma la ruta de salida para audio multimedia.")
    }
    when {
        capabilities == null ->
            reasons.add("Android no declara capacidades del dispositivo de salida.")
        capabilities.capabilityState == CapabilityState.UNKNOWN ->
            reasons.add("Las capacidades declaradas del dispositivo están incompletas.")
    }
    reasons.add("El formato efectivo recibido por el DAC no es verificable con APIs públicas.")
    return reasons
}

/** Etiqueta legible del códec de origen; [AudioCodec.UNKNOWN] no es "sin códec". */
internal fun AudioCodec.displayLabel(): String = when (this) {
    AudioCodec.FLAC -> "FLAC"
    AudioCodec.WAV -> "WAV/PCM"
    AudioCodec.ALAC -> "ALAC"
    AudioCodec.AIFF -> "AIFF"
    AudioCodec.MP3 -> "MP3"
    AudioCodec.AAC -> "AAC"
    AudioCodec.OGG -> "OGG"
    AudioCodec.UNKNOWN -> "No identificado"
}

/**
 * Etiqueta del estado de bit-perfect, sin afirmar el formato efectivo del DAC.
 *
 * En la Etapa 1 no existe evidencia de la ruta/DAC, por lo que [BitPerfectStatus.CONVERTED]
 * (que el clasificador también devuelve para un origen con pérdidas) se muestra como
 * "No confirmado": no se atribuye una incompatibilidad de la ruta a la naturaleza del
 * archivo.
 */
internal fun BitPerfectStatus.displayLabel(): String = when (this) {
    BitPerfectStatus.BIT_PERFECT -> "No confirmado"
    BitPerfectStatus.LOSSLESS -> "No confirmado"
    BitPerfectStatus.CONVERTED -> "No confirmado"
    BitPerfectStatus.UNAVAILABLE -> "No confirmado"
}

/** "sin pérdidas"/"con pérdidas" solo si se determinó; si no, "sin determinar". */
internal fun Boolean?.losslessDescription(): String = when (this) {
    true -> "sin pérdidas"
    false -> "con pérdidas"
    null -> "sin determinar"
}

/**
 * Resumen del formato de origen. Omite el prefijo "Origen:"/"Formato de origen:" para
 * que cada pantalla lo componga a su medida. Los desconocidos se representan con `null`
 * y los valores no positivos (0/negativos) se tratan como desconocidos: nunca se
 * muestran como datos reales.
 */
internal fun AudioPathDiagnostics.sourceSummary(): String {
    if (!hasSource) return "No disponible (sin pista en reproducción)"
    val details = mutableListOf(sourceCodec.displayLabel())
    details.add(sourceSampleRateHz?.takeIf { it > 0 }?.let { "$it Hz" } ?: "muestreo desconocido")
    details.add(sourceBitDepth?.takeIf { it > 0 }?.let { "$it bits" } ?: "profundidad desconocida")
    details.add(sourceChannelCount?.takeIf { it > 0 }?.let { "$it canales" } ?: "canales desconocidos")
    sourceBitRate?.takeIf { it > 0 }?.let { details.add(formatBitRate(it)) }
    return details.joinToString(" · ")
}

/**
 * Aviso fijo: el formato seleccionado declara el contenedor/archivo, no el PCM
 * decodificado ni lo que recibe realmente el DAC. Se muestra junto al dato para no
 * hacer pasar una cosa por otra.
 */
internal const val SELECTED_FORMAT_DISCLAIMER: String =
    "El formato seleccionado procede del contenedor del archivo; no es el PCM " +
        "decodificado ni el formato efectivo recibido por el DAC."

/**
 * Resumen del formato **seleccionado por el reproductor** (Etapa 2a). No incluye
 * prefijo para que cada pantalla lo componga. Los desconocidos y los no positivos
 * (0/negativos) se tratan como desconocidos; nunca se inventa un valor. Si hay varias
 * pistas seleccionadas a la vez, se declara la ambigüedad en lugar de elegir una.
 */
internal fun AudioPathDiagnostics.selectedSummary(): String {
    return when (val selected = selectedFormat) {
        null ->
            if (hasSource) "No disponible (sin selección del reproductor)"
            else "No disponible (sin pista en reproducción)"
        is SelectedTrackFormat.Ambiguous ->
            "Ambiguo: ${selected.selectedTrackCount} pistas de audio seleccionadas a la vez"
        is SelectedTrackFormat.Single -> {
            val details = mutableListOf(selected.codec.displayLabel())
            details.add(selected.sampleRateHz?.takeIf { it > 0 }?.let { "$it Hz" } ?: "muestreo desconocido")
            details.add(selected.channelCount?.takeIf { it > 0 }?.let { "$it canales" } ?: "canales desconocidos")
            details.add(selected.bitDepth?.takeIf { it > 0 }?.let { "$it bits" } ?: "profundidad desconocida")
            selected.bitRate?.takeIf { it > 0 }?.let { details.add(formatBitRate(it)) }
            details.joinToString(" · ")
        }
    }
}

/** Formatea un bitrate en bits/s como kbps cuando procede. */
internal fun formatBitRate(bitsPerSecond: Long): String =
    if (bitsPerSecond >= 1_000L) "${bitsPerSecond / 1_000L} kbps" else "$bitsPerSecond bps"

/**
 * Aviso fijo: el formato de entrada al decodificador describe el stream tal como
 * llega al decodificador (puede ser comprimido). No es el PCM de salida ni el
 * formato del DAC.
 */
internal const val DECODER_INPUT_DISCLAIMER: String =
    "El formato de entrada al decodificador describe el stream comprimido o PCM " +
        "tal como llega al decodificador; no es el PCM de salida ni el formato del DAC."

/**
 * Resumen del formato de entrada al decodificador (Etapa 2b).
 * Observado vía `AnalyticsListener.onAudioInputFormatChanged`.
 */
internal fun AudioPathDiagnostics.decoderInputSummary(): String {
    return when (val input = decoderInputFormat) {
        null ->
            if (hasSource) "No disponible (sin dato del decodificador)"
            else "No disponible (sin pista en reproducción)"
        else -> {
            val details = mutableListOf(input.codec.displayLabel())
            details.add(input.mimeType?.takeIf { it.isNotBlank() } ?: "MIME no declarado")
            details.add(input.sampleRateHz?.takeIf { it > 0 }?.let { "$it Hz" } ?: "muestreo desconocido")
            details.add(input.channelCount?.takeIf { it > 0 }?.let { "$it canales" } ?: "canales desconocidos")
            details.add(input.bitDepth?.takeIf { it > 0 }?.let { "$it bits" } ?: "profundidad desconocida")
            input.bitRate?.takeIf { it > 0 }?.let { details.add(formatBitRate(it)) }
            details.joinToString(" · ")
        }
    }
}

/**
 * Aviso fijo: la configuración del AudioTrack es lo que ExoPlayer configura;
 * el mixer del sistema puede alterarlo. No es el formato efectivo del DAC.
 */
internal const val AUDIO_TRACK_CONFIG_DISCLAIMER: String =
    "La configuración del AudioTrack es lo que ExoPlayer solicita al sistema; " +
        "el mixer de Android puede resamplear o cambiar canales. No es el formato efectivo del DAC."

/**
 * Resumen de la configuración del AudioTrack (Etapa 2b).
 * Observado vía `AnalyticsListener.onAudioTrackInitialized` → `AudioSink.AudioTrackConfig`.
 */
internal fun AudioPathDiagnostics.audioTrackConfigSummary(): String {
    return when (val config = audioTrackConfig) {
        null ->
            if (hasSource) "No disponible (sin configuración de AudioTrack)"
            else "No disponible (sin pista en reproducción)"
        else -> {
            val details = mutableListOf(config.encoding.displayLabel())
            details.add(config.sampleRateHz?.takeIf { it > 0 }?.let { "$it Hz" } ?: "muestreo desconocido")
            details.add(config.channelCount?.takeIf { it > 0 }?.let { "$it canales" } ?: "canales desconocidos")
            config.bufferSizeBytes?.takeIf { it > 0 }?.let { details.add("buffer ${it} B") }
            if (config.tunneling) details.add("túnel")
            if (config.offload) details.add("offload")
            details.joinToString(" · ")
        }
    }
}
