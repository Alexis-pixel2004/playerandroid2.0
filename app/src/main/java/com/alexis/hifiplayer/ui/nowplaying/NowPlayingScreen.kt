package com.alexis.hifiplayer.ui.nowplaying

import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alexis.hifiplayer.domain.model.AudioCodec
import com.alexis.hifiplayer.domain.model.AudioDevice
import com.alexis.hifiplayer.domain.model.AudioDeviceCapabilities
import com.alexis.hifiplayer.domain.model.AudioDeviceType
import com.alexis.hifiplayer.domain.model.PcmEncoding
import com.alexis.hifiplayer.domain.model.Song
import com.alexis.hifiplayer.ui.audioddevice.AudioDeviceUiState
import com.alexis.hifiplayer.ui.audioddevice.AudioPathDiagnostics
import com.alexis.hifiplayer.ui.audioddevice.AUDIO_TRACK_CONFIG_DISCLAIMER
import com.alexis.hifiplayer.ui.audioddevice.DECODER_INPUT_DISCLAIMER
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.roundToLong

/** Tamaño solicitado para la miniatura de carátula (px). */
private const val ArtworkSizePx = 768

/**
 * Pantalla de detalle "Ahora suena".
 *
 * Muestra la carátula (derivada de [Song.contentUri]) y los metadatos disponibles,
 * una barra de progreso con la posición real del motor y los controles de
 * reproducción reales.
 *
 * La canción mostrada y el estado de los controles provienen del estado de
 * reproducción (la UI no los simula ni los mantiene localmente): el icono central es
 * [AppIcons.Pause] cuando [isPlaying] y [AppIcons.PlayArrow] en caso contrario, y
 * Anterior/Siguiente se habilitan según [hasPrevious]/[hasNext].
 *
 * El progreso usa [positionMs] y la duración real [durationMs]: el deslizador queda
 * habilitado solo si la duración es válida y, al soltarlo, busca con [onSeekTo].
 * Mientras se arrastra, un estado local mantiene el pulgar en el valor elegido sin
 * que la actualización periódica lo mueva; al cambiar de canción se reinicia.
 *
 * No afirma el formato efectivo recibido por el DAC: ese no es verificable con APIs
 * públicas. Muestra un resumen del dispositivo de salida, sin afirmar bit-perfect.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    song: Song,
    isPlaying: Boolean,
    hasNext: Boolean,
    hasPrevious: Boolean,
    positionMs: Long,
    durationMs: Long?,
    audioDeviceState: AudioDeviceUiState,
    audioPathDiagnostics: AudioPathDiagnostics,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Carga la miniatura fuera del hilo principal. produceState cancela la corrutina
    // al salir de composición y la CancellationSignal cancela la operación en curso.
    val artwork by produceState<ImageBitmap?>(initialValue = null, song.contentUri) {
        value = loadArtwork(context, song.contentUri)
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Ahora suena", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painter = painterResource(AppIcons.ArrowBack), contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors()
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Artwork(artwork = artwork)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = song.title.ifBlank { "Título desconocido" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = song.artist.ifBlank { "Artista desconocido" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (song.album.isNotBlank()) {
                Text(
                    text = song.album,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            ProgressSection(
                songId = song.id,
                positionMs = positionMs,
                durationMs = durationMs,
                onSeekTo = onSeekTo
            )

            Spacer(modifier = Modifier.height(8.dp))

            PlaybackControls(
                isPlaying = isPlaying,
                hasNext = hasNext,
                hasPrevious = hasPrevious,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutputDeviceSummary(state = audioDeviceState, diagnostics = audioPathDiagnostics)
        }
    }
}

/**
 * Resumen compacto del dispositivo de salida. Distingue lo enrutado de lo detectado
 * y etiqueta las capacidades como "declaradas por Android": no afirma bit-perfect
 * ni el formato efectivo recibido por el DAC. Añade el origen y el estado de
 * bit-perfect con la misma honestidad.
 */
@Composable
private fun OutputDeviceSummary(
    state: AudioDeviceUiState,
    diagnostics: AudioPathDiagnostics
) {
    val routed = state.routedDevice
    val caps = state.capabilities

    val deviceText = when {
        state.errorMessage != null -> "Dispositivo de salida: no disponible"
        routed == null -> "Dispositivo de salida: no confirmado por Android"
        else -> buildString {
            append("Dispositivo de salida: ")
            append(routed.displayName())
            append(" · ")
            append(routed.type.displayLabel())
            if (routed.isRouted == true) {
                append(" · Enrutado según Android")
            }
        }
    }

    val capabilitiesText: String? = when {
        routed == null -> null
        caps == null -> "Capacidades declaradas: no disponibles"
        caps.supportedSampleRates.isEmpty() &&
            caps.supportedChannelCounts.isEmpty() &&
            caps.supportedEncodings.isEmpty() ->
            "Capacidades declaradas: no declaradas por Android"
        else ->
            "Capacidades declaradas (Android): " +
                "${formatSampleRates(caps.supportedSampleRates)} · " +
                "${formatChannelCounts(caps.supportedChannelCounts)} canales · " +
                formatEncodings(caps.supportedEncodings)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = deviceText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (capabilitiesText != null) {
            Text(
                text = capabilitiesText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Text(
            text = "Origen: ${diagnostics.sourceSummary()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Seleccionado por el reproductor: ${diagnostics.selectedSummary()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Entrada decodificador: ${diagnostics.decoderInputSummary()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text = DECODER_INPUT_DISCLAIMER,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Text(
            text = "AudioTrack: ${diagnostics.audioTrackConfigSummary()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text = AUDIO_TRACK_CONFIG_DISCLAIMER,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Text(
            text = "Calidad del origen: ${diagnostics.sourceIsLossless.losslessDescription()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Bit-perfect: ${diagnostics.bitPerfectStatus.displayLabel()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun Artwork(artwork: ImageBitmap?) {
    Box(
        modifier = Modifier
            .size(280.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        if (artwork != null) {
            Image(
                bitmap = artwork,
                contentDescription = "Carátula",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                painter = painterResource(AppIcons.MusicNote),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )
        }
    }
}

@Composable
private fun ProgressSection(
    songId: Long,
    positionMs: Long,
    durationMs: Long?,
    onSeekTo: (Long) -> Unit
) {
    // Duración válida del motor: `null` o `<= 0` deshabilita el deslizador.
    val duration = durationMs?.takeIf { it > 0L }

    // Estado efímero de arrastre (no duplica el estado de reproducción). Se reinicia
    // al cambiar de canción porque está recordado por `songId`.
    var isDragging by remember(songId) { mutableStateOf(false) }
    var dragFraction by remember(songId) { mutableFloatStateOf(0f) }

    // Fracción 0..1 del progreso. Se calcula con Double (sin perder precisión al
    // mezclar Long/Float) y se convierte a Float al final.
    val stateFraction = if (duration != null) {
        (positionMs.toDouble() / duration.toDouble()).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
    val shownFraction = if (isDragging) dragFraction else stateFraction

    // Tiempo transcurrido mostrado: sigue al pulgar durante el arrastre; en reposo es
    // la posición real del motor. Acotado a [0, duración].
    val shownPositionMs = if (duration != null) {
        (shownFraction.toDouble() * duration).roundToLong().coerceIn(0L, duration)
    } else {
        positionMs.coerceAtLeast(0L)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = shownFraction,
            onValueChange = { fraction ->
                if (duration != null) {
                    isDragging = true
                    dragFraction = fraction
                }
            },
            onValueChangeFinished = {
                if (duration != null) {
                    val target = (dragFraction.toDouble() * duration)
                        .roundToLong()
                        .coerceIn(0L, duration)
                    onSeekTo(target)
                    isDragging = false
                }
            },
            enabled = duration != null,
            colors = SliderDefaults.colors(
                // Colores habilitados explícitos derivados del esquema activo: el
                // acento sigue el rol `primary` (dinámico o baseline según Ajustes).
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledThumbColor = MaterialTheme.colorScheme.primary,
                disabledActiveTrackColor = MaterialTheme.colorScheme.primary,
                disabledInactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(shownPositionMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (duration != null) formatDuration(duration) else "--:--",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    hasNext: Boolean,
    hasPrevious: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious, enabled = hasPrevious) {
            Icon(
                painter = painterResource(AppIcons.SkipPrevious),
                contentDescription = "Anterior",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(modifier = Modifier.size(16.dp))
        IconButton(onClick = onPlayPause, modifier = Modifier.size(64.dp)) {
            Icon(
                painter = painterResource(
                    if (isPlaying) AppIcons.Pause else AppIcons.PlayArrow
                ),
                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.size(16.dp))
        IconButton(onClick = onNext, enabled = hasNext) {
            Icon(
                painter = painterResource(AppIcons.SkipNext),
                contentDescription = "Siguiente",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

/**
 * Carga la miniatura de la carátula desde [uriString] fuera del hilo principal.
 * Devuelve null si no hay carátula o si algo falla, para mostrar el placeholder.
 * La operación se cancela si la corrutina se cancela (p. ej. al salir de pantalla).
 */
private suspend fun loadArtwork(context: Context, uriString: String): ImageBitmap? {
    if (uriString.isEmpty()) return null
    return withContext(Dispatchers.IO) {
        val signal = CancellationSignal()
        suspendCancellableCoroutine<ImageBitmap?> { continuation ->
            continuation.invokeOnCancellation { signal.cancel() }
            try {
                val bitmap = context.contentResolver.loadThumbnail(
                    Uri.parse(uriString),
                    Size(ArtworkSizePx, ArtworkSizePx),
                    signal
                )
                if (continuation.isActive) continuation.resume(bitmap.asImageBitmap())
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format("%02d:%02d", minutes, seconds)
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun NowPlayingScreenPreview() {
    HiFiPlayerTheme {
        NowPlayingScreen(
            song = Song(
                title = "Pista de prueba",
                artist = "Artista",
                album = "Álbum",
                durationMs = 215000L
            ),
            isPlaying = false,
            hasNext = true,
            hasPrevious = false,
            positionMs = 60000L,
            durationMs = 215000L,
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
            ),
            onPlayPause = { },
            onNext = { },
            onPrevious = { },
            onSeekTo = { },
            onBack = { }
        )
    }
}
