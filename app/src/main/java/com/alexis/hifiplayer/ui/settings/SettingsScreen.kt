package com.alexis.hifiplayer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexis.hifiplayer.domain.model.AudioCodec
import com.alexis.hifiplayer.domain.model.AudioDevice
import com.alexis.hifiplayer.domain.model.AudioDeviceCapabilities
import com.alexis.hifiplayer.domain.model.AudioDeviceType
import com.alexis.hifiplayer.domain.model.PcmEncoding
import com.alexis.hifiplayer.domain.model.ThemeMode
import com.alexis.hifiplayer.ui.audioddevice.AudioDeviceUiState
import com.alexis.hifiplayer.ui.audioddevice.AudioPathDiagnostics
import com.alexis.hifiplayer.ui.audioddevice.AUDIO_TRACK_CONFIG_DISCLAIMER
import com.alexis.hifiplayer.ui.audioddevice.DECODER_INPUT_DISCLAIMER
import com.alexis.hifiplayer.ui.audioddevice.SELECTED_FORMAT_DISCLAIMER
import com.alexis.hifiplayer.ui.audioddevice.audioTrackConfigSummary
import com.alexis.hifiplayer.ui.audioddevice.decoderInputSummary
import com.alexis.hifiplayer.ui.audioddevice.displayLabel
import com.alexis.hifiplayer.ui.audioddevice.displayName
import com.alexis.hifiplayer.ui.audioddevice.formatChannelCounts
import com.alexis.hifiplayer.ui.audioddevice.formatEncodings
import com.alexis.hifiplayer.ui.audioddevice.formatSampleRates
import com.alexis.hifiplayer.ui.audioddevice.losslessDescription
import com.alexis.hifiplayer.ui.audioddevice.selectedSummary
import com.alexis.hifiplayer.ui.audioddevice.sourceSummary
import com.alexis.hifiplayer.ui.icons.AppIcons
import com.alexis.hifiplayer.ui.theme.HiFiPlayerTheme

/**
 * Pantalla de Ajustes.
 *
 * El modo de tema se observa directamente del [SettingsViewModel] (misma instancia
 * que usa `MainActivity`): no se guarda ninguna copia local de la preferencia. Al
 * seleccionar una opción se delega en el ViewModel, que persiste el cambio; la
 * aplicación del nuevo esquema la propaga el flujo de `MainActivity` hacia
 * `HiFiPlayerTheme`.
 *
 * El estado del dispositivo de salida ([audioDeviceState]) y el diagnóstico de la ruta
 * ([audioPathDiagnostics]) llegan ya calculados desde `AppRoot` (una única fuente
 * compartida con "Ahora suena"): esta pantalla no consulta dispositivos por su cuenta.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    audioDeviceState: AudioDeviceUiState,
    audioPathDiagnostics: AudioPathDiagnostics,
    modifier: Modifier = Modifier
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    SettingsContent(
        themeMode = themeMode,
        onThemeModeChange = viewModel::setThemeMode,
        audioDeviceState = audioDeviceState,
        audioPathDiagnostics = audioPathDiagnostics,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    audioDeviceState: AudioDeviceUiState,
    audioPathDiagnostics: AudioPathDiagnostics,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Ajustes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors()
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter = painterResource(AppIcons.Settings),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Ajustes",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Apariencia",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Elige el origen de los colores de la aplicación.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = themeMode == mode,
                        onClick = { onThemeModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ThemeMode.entries.size
                        ),
                        label = { Text(text = mode.label) }
                    )
                }
            }
            Text(
                text = themeMode.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AudioOutputSection(state = audioDeviceState, diagnostics = audioPathDiagnostics)
        }
    }
}

/**
 * Sección de "Dispositivo de audio": muestra lo que Android declara, distinguiendo
 * lo detectado de lo enrutado. Añade el diagnóstico de la ruta (formato de origen y
 * estado de bit-perfect), sin afirmar el formato real recibido por el DAC ni
 * declarar bit-perfect con la información disponible.
 */
@Composable
private fun AudioOutputSection(
    state: AudioDeviceUiState,
    diagnostics: AudioPathDiagnostics
) {
    Text(
        text = "Dispositivo de audio",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground
    )

    val routed = state.routedDevice
    val error = state.errorMessage

    if (error != null) {
        InfoLine(error)
    } else if (routed != null) {
        InfoLine("Dispositivo: ${routed.displayName()}")
        InfoLine("Tipo: ${routed.type.displayLabel()}")
        InfoLine(
            "Enrutado según Android: " +
                if (routed.isRouted == true) "sí" else "no confirmado"
        )
    } else {
        InfoLine(
            "Android no confirma un dispositivo de salida enrutado para reproducción multimedia."
        )
    }

    if (state.availableDevices.isNotEmpty()) {
        InfoLine("Salidas detectadas: ${state.availableDevices.size}")
        state.availableDevices.forEach { device ->
            val routedMark = if (device.isRouted == true) " · enrutado" else ""
            InfoLine("• ${device.displayName()} (${device.type.displayLabel()})$routedMark")
        }
    }

    if (routed != null) {
        val caps = state.capabilities
        InfoLine(
            "Capacidades declaradas por Android (no garantizan el formato efectivo recibido por el DAC):"
        )
        InfoLine("Frecuencias de muestreo: ${formatSampleRates(caps?.supportedSampleRates.orEmpty())}")
        InfoLine("Canales: ${formatChannelCounts(caps?.supportedChannelCounts.orEmpty())}")
        InfoLine("Codificaciones: ${formatEncodings(caps?.supportedEncodings.orEmpty())}")
    }

    Text(
        text = "Formato de origen",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onBackground
    )
    InfoLine(diagnostics.sourceSummary())
    InfoLine("Tipo: ${diagnostics.sourceIsLossless.losslessDescription()}")

    Text(
        text = "Formatos de la ruta",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onBackground
    )
    InfoLine("Seleccionado por el reproductor: ${diagnostics.selectedSummary()}")
    InfoLine(SELECTED_FORMAT_DISCLAIMER)
    InfoLine("Entrada al decodificador: ${diagnostics.decoderInputSummary()}")
    InfoLine(DECODER_INPUT_DISCLAIMER)
    InfoLine("AudioTrack configurado: ${diagnostics.audioTrackConfigSummary()}")
    InfoLine(AUDIO_TRACK_CONFIG_DISCLAIMER)
    InfoLine("Solicitado/configurado para la salida: no disponible en esta etapa.")
    InfoLine("Efectivo recibido por el DAC: no verificable con la información disponible.")

    Text(
        text = "Bit-perfect",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onBackground
    )
    InfoLine("Estado: ${diagnostics.bitPerfectStatus.displayLabel()}")
    diagnostics.reasons.forEach { reason -> InfoLine("• $reason") }

    if (routed?.type == AudioDeviceType.BLUETOOTH_HEADPHONES) {
        InfoLine(
            "Aviso: la salida es Bluetooth (inferido por el tipo de dispositivo detectado). " +
                "Bluetooth suele recodificar el audio y no es bit-perfect; no es una medición."
        )
    }

    state.uncertainties.forEach { InfoLine("Nota: $it") }
}

@Composable
private fun InfoLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.DYNAMIC -> "Dinámico"
        ThemeMode.DEFAULT -> "Predeterminado"
    }

private val ThemeMode.description: String
    get() = when (this) {
        ThemeMode.DYNAMIC -> "Usa los colores derivados del fondo de pantalla del sistema."
        ThemeMode.DEFAULT -> "Usa el esquema de colores estándar de Material 3."
    }

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SettingsScreenPreview() {
    HiFiPlayerTheme {
        SettingsContent(
            themeMode = ThemeMode.DYNAMIC,
            onThemeModeChange = {},
            audioPathDiagnostics = AudioPathDiagnostics(
                hasSource = true,
                sourceCodec = AudioCodec.FLAC,
                sourceIsLossless = true,
                sourceSampleRateHz = 96000,
                sourceBitDepth = 24,
                sourceChannelCount = 2
            ),
            audioDeviceState = AudioDeviceUiState(
                isLoading = false,
                routedDevice = AudioDevice(
                    id = "1",
                    name = "Altavoz del teléfono",
                    type = AudioDeviceType.PHONE_SPEAKER,
                    isConnected = true,
                    isRouted = true
                ),
                capabilities = AudioDeviceCapabilities(
                    supportedSampleRates = listOf(48000),
                    supportedChannelCounts = listOf(2),
                    supportedEncodings = listOf(PcmEncoding.PCM_16)
                )
            )
        )
    }
}
