package com.alexis.hifiplayer.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexis.hifiplayer.ui.audioddevice.AudioDeviceViewModel
import com.alexis.hifiplayer.ui.audioddevice.buildAudioPathDiagnostics
import com.alexis.hifiplayer.ui.home.HomeScreen
import com.alexis.hifiplayer.ui.icons.AppIcons
import com.alexis.hifiplayer.ui.library.LibraryScreen
import com.alexis.hifiplayer.ui.library.LibraryViewModel
import com.alexis.hifiplayer.ui.nowplaying.NowPlayingScreen
import com.alexis.hifiplayer.ui.playback.PlaybackViewModel
import com.alexis.hifiplayer.ui.settings.SettingsScreen
import com.alexis.hifiplayer.ui.settings.SettingsViewModel

/**
 * Destinos de la barra inferior. El orden define el orden visual de la barra.
 */
private enum class AppDestination(val label: String) {
    HOME("Inicio"),
    LIBRARY("Biblioteca"),
    SETTINGS("Ajustes")
}

@DrawableRes
private fun AppDestination.iconRes(): Int = when (this) {
    AppDestination.HOME -> AppIcons.Home
    AppDestination.LIBRARY -> AppIcons.MenuBook
    AppDestination.SETTINGS -> AppIcons.Settings
}

/**
 * Guarda el destino como su [Enum.name] para que sobreviva a la rotación y a la
 * recreación de la actividad (rememberSaveable).
 */
private val AppDestinationSaver: Saver<AppDestination, String> = Saver(
    save = { it.name },
    restore = { name -> AppDestination.valueOf(name) }
)

/**
 * Contenedor raíz de la aplicación: un único Scaffold con la barra de navegación
 * inferior persistente y el contenido del destino seleccionado.
 *
 * - La pestaña seleccionada se conserva durante la rotación (rememberSaveable).
 * - Un arranque nuevo de la aplicación comienza siempre en Inicio.
 * - Atrás (sistema y flecha de la TopAppBar de Biblioteca) vuelve a Inicio.
 * - El ViewModel de Biblioteca se recibe ya creado (se conserva su ciclo de vida).
 * - El ViewModel de Reproducción se recibe ya creado (propietario único del motor,
 *   de ámbito de actividad) y es la fuente de la canción mostrada en "Ahora suena".
 * - El ViewModel de Ajustes se recibe ya creado y se pasa a la pantalla de Ajustes,
 *   que observa y persiste la preferencia de tema (misma instancia que `MainActivity`).
 * - El ViewModel del dispositivo de salida se recibe ya creado. Su estado se recoge
 *   **una sola vez** aquí y se entrega a Ajustes y a "Ahora suena", de modo que ambas
 *   pantallas comparten una única fuente de verdad (sin consultas duplicadas).
 * - "Ahora suena" es un detalle a pantalla completa (sin barra inferior). La bandera
 *   `showNowPlaying` solo indica si está abierto; el contenido sale de
 *   `PlaybackState.currentSong`.
 */
@Composable
fun AppRoot(
    libraryViewModel: LibraryViewModel,
    playbackViewModel: PlaybackViewModel,
    settingsViewModel: SettingsViewModel,
    audioDeviceViewModel: AudioDeviceViewModel,
    modifier: Modifier = Modifier
) {
    var selected by rememberSaveable(stateSaver = AppDestinationSaver) {
        mutableStateOf(AppDestination.HOME)
    }

    // Bandera de navegación del detalle "Ahora suena". No guarda la canción: el
    // contenido sale siempre del estado de reproducción (PlaybackState.currentSong).
    var showNowPlaying by rememberSaveable { mutableStateOf(false) }

    val libraryState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by playbackViewModel.state.collectAsStateWithLifecycle()

    // Estado del dispositivo de salida: se recoge una única vez y se comparte con
    // Ajustes y "Ahora suena". Al volver a primer plano, collectAsStateWithLifecycle
    // reanuda la colección y el ViewModel refresca el estado (sin polling permanente).
    val audioDeviceState by audioDeviceViewModel.state.collectAsStateWithLifecycle()

    // Solo se deriva la canción actual: el contenedor (y el destino visible, p. ej.
    // la biblioteca) solo deben recomponerse cuando cambia la pista, no en cada
    // refresco de posición (cada 500 ms). `derivedStateOf` usa igualdad estructural,
    // de modo que no notifica a sus lectores si el `Song` resultante es el mismo.
    // Las lecturas de posición/estado de reproducción ocurren únicamente dentro de la
    // rama de "Ahora suena", por lo que no suscriben a este contenedor cuando el
    // detalle está cerrado.
    val currentSong by remember {
        derivedStateOf { playbackState.currentSong }
    }
    // Formato seleccionado por el reproductor (Etapa 2a). Igual que con la canción, se
    // deriva para no recomponer el contenedor en cada refresco de posición: la selección
    // cambia con la pista (o al perder la sesión), no con el avance del tiempo.
    val selectedTrackFormat by remember {
        derivedStateOf { playbackState.selectedTrackFormat }
    }
    // Formato de entrada al decodificador y configuración AudioTrack (Etapa 2b).
    val decoderInputFormat by remember {
        derivedStateOf { playbackState.decoderInputFormat }
    }
    val audioTrackConfig by remember {
        derivedStateOf { playbackState.audioTrackConfig }
    }
    // Copia local no delegada: una propiedad delegada no admite smart-cast a no-nulo
    // (su valor podría cambiar entre la comprobación y el uso). `song` es un `val`
    // normal, por lo que `song != null` sí estrecha el tipo. No duplica estado: es una
    // lectura del mismo valor derivado en esta composición.
    val song = currentSong

    // Diagnóstico de la ruta de audio (Etapa 1 + 2a + 2b): se calcula una sola vez a partir del
    // formato de origen, del dispositivo enrutado, del formato seleccionado por el
    // reproductor, del formato de entrada al decodificador y de la configuración AudioTrack,
    // y se comparte con Ajustes y "Ahora suena" para no duplicar cálculos.
    val audioPathDiagnostics = remember(audioDeviceState, song, selectedTrackFormat, decoderInputFormat, audioTrackConfig) {
        buildAudioPathDiagnostics(song, audioDeviceState, selectedTrackFormat, decoderInputFormat, audioTrackConfig)
    }

    // Si se pidió mostrar el detalle pero ya no hay canción actual (cola vacía), se
    // vuelve a la vista principal para no renderizar una pantalla que necesita canción.
    LaunchedEffect(showNowPlaying, song) {
        if (showNowPlaying && song == null) {
            showNowPlaying = false
        }
    }

    // Atrás: primero cierra "Ahora suena" (vuelve a la pestaña anterior); si no, vuelve
    // a Inicio desde otra pestaña. En Inicio no se intercepta.
    BackHandler(enabled = showNowPlaying || selected != AppDestination.HOME) {
        if (showNowPlaying) {
            showNowPlaying = false
        } else {
            selected = AppDestination.HOME
        }
    }

    if (showNowPlaying && song != null) {
        NowPlayingScreen(
            song = song,
            isPlaying = playbackState.isPlaying,
            hasNext = playbackState.hasNext,
            hasPrevious = playbackState.hasPrevious,
            positionMs = playbackState.positionMs,
            durationMs = playbackState.durationMs,
            audioDeviceState = audioDeviceState,
            audioPathDiagnostics = audioPathDiagnostics,
            onPlayPause = { playbackViewModel.togglePlayPause() },
            onNext = { playbackViewModel.next() },
            onPrevious = { playbackViewModel.previous() },
            onSeekTo = { positionMs -> playbackViewModel.seekTo(positionMs) },
            onBack = { showNowPlaying = false },
            modifier = modifier
        )
    } else {
        Scaffold(
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.background,
            // El contenido de cada destino gestiona sus propios insets superiores
            // (cada pantalla tiene su TopAppBar). Así evitamos doble padding arriba.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    AppDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = selected == destination,
                            onClick = { selected = destination },
                            icon = { Icon(painter = painterResource(destination.iconRes()), contentDescription = null) },
                            label = { Text(text = destination.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Consume el padding de la barra para que los Scaffolds internos
                    // no vuelvan a aplicar el inset inferior (evita doble padding).
                    .consumeWindowInsets(innerPadding)
                    .padding(innerPadding)
            ) {
                when (selected) {
                    AppDestination.HOME -> HomeScreen(
                        onOpenLibrary = { selected = AppDestination.LIBRARY }
                    )
                    AppDestination.LIBRARY -> LibraryScreen(
                        viewModel = libraryViewModel,
                        onBack = { selected = AppDestination.HOME },
                        onSongClick = { song ->
                            // La cola es la lista actual de la biblioteca, en su orden
                            // (no se reordena ni se filtra una segunda lista).
                            val queue = libraryState.songs
                            val index = queue.indexOfFirst { it.id == song.id }
                            if (index >= 0) {
                                playbackViewModel.setQueue(queue, index)
                                playbackViewModel.play()
                                showNowPlaying = true
                            }
                            // Si no se encuentra (index < 0) no se toca el controlador
                            // ni se abre "Ahora suena".
                        }
                    )
                    AppDestination.SETTINGS -> SettingsScreen(
                        viewModel = settingsViewModel,
                        audioDeviceState = audioDeviceState,
                        audioPathDiagnostics = audioPathDiagnostics
                    )
                }
            }
        }
    }
}
