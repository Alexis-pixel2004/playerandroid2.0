package com.alexis.hifiplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexis.hifiplayer.data.device.AndroidAudioDeviceProvider
import com.alexis.hifiplayer.data.device.AudioDeviceMonitor
import com.alexis.hifiplayer.data.media.MediaStoreDataSource
import com.alexis.hifiplayer.data.playback.MediaSessionPlaybackController
import com.alexis.hifiplayer.data.repository.MediaStoreLibraryRepository
import com.alexis.hifiplayer.data.repository.PreferencesAudioSettingsRepository
import com.alexis.hifiplayer.data.repository.PreferencesThemeSettingsRepository
import com.alexis.hifiplayer.domain.repository.AudioSettingsRepository
import com.alexis.hifiplayer.domain.repository.LibraryRepository
import com.alexis.hifiplayer.domain.repository.ThemeSettingsRepository
import com.alexis.hifiplayer.domain.usecase.GetAudioDevicesUseCase
import com.alexis.hifiplayer.ui.AppRoot
import com.alexis.hifiplayer.ui.audioddevice.AudioDeviceViewModel
import com.alexis.hifiplayer.ui.audioddevice.AudioDeviceViewModelFactory
import com.alexis.hifiplayer.ui.library.LibraryViewModel
import com.alexis.hifiplayer.ui.library.LibraryViewModelFactory
import com.alexis.hifiplayer.ui.playback.PlaybackViewModel
import com.alexis.hifiplayer.ui.playback.PlaybackViewModelFactory
import com.alexis.hifiplayer.ui.settings.SettingsViewModel
import com.alexis.hifiplayer.ui.settings.SettingsViewModelFactory
import com.alexis.hifiplayer.ui.theme.HiFiPlayerTheme

class MainActivity : ComponentActivity() {

    // El repositorio se crea una sola vez por actividad y se reutiliza,
    // de modo que no se crea un ContentObserver nuevo por pestaña.
    private val libraryRepository: LibraryRepository by lazy {
        MediaStoreLibraryRepository(applicationContext, MediaStoreDataSource(applicationContext))
    }

    // ViewModel ligado al ciclo de vida de la actividad: sobrevive a la rotación
    // y su onCleared() se invoca cuando la actividad termina definitivamente.
    private val libraryViewModel: LibraryViewModel by viewModels {
        LibraryViewModelFactory(libraryRepository)
    }

    // Único cliente de la sesión de reproducción, compartido por toda la actividad;
    // el motor real (ExoPlayer) pertenece a PlaybackService. El proveedor NO construye
    // el controlador aquí: PlaybackViewModelFactory.create() lo construye solo cuando
    // el ViewModel se crea de verdad (no en cada rotación).
    private val playbackViewModel: PlaybackViewModel by viewModels {
        PlaybackViewModelFactory { MediaSessionPlaybackController(applicationContext) }
    }

    // Repositorio de la preferencia de tema: se crea una sola vez por actividad y
    // comparte la instancia única de DataStore de PreferencesThemeSettingsRepository.
    private val themeSettingsRepository: ThemeSettingsRepository by lazy {
        PreferencesThemeSettingsRepository(applicationContext)
    }

    // Repositorio de preferencias de audio: se crea una sola vez por actividad y
    // comparte la instancia única de DataStore de PreferencesAudioSettingsRepository.
    private val audioSettingsRepository: AudioSettingsRepository by lazy {
        PreferencesAudioSettingsRepository(applicationContext)
    }

    // ViewModel ligado a la actividad: sobrevive a la rotación y solo se crea una vez.
    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(themeSettingsRepository)
    }

    // Proveedor del dispositivo de salida: se crea una sola vez por actividad y se
    // comparte con el monitor y con el caso de uso (evita instancias duplicadas).
    private val audioDeviceProvider: AndroidAudioDeviceProvider by lazy {
        AndroidAudioDeviceProvider(applicationContext)
    }

    // Monitor de cambios de dispositivos: reutiliza el mismo proveedor y expone un
    // flujo de avisos (no hace polling; se apoya en AudioDeviceCallback).
    private val audioDeviceMonitor: AudioDeviceMonitor by lazy {
        AudioDeviceMonitor(applicationContext, audioDeviceProvider)
    }

    // ViewModel de ámbito de actividad: única fuente de estado del dispositivo para
    // Ajustes y "Ahora suena". La factoría construye el ViewModel solo cuando se crea
    // de verdad (no en cada rotación).
    private val audioDeviceViewModel: AudioDeviceViewModel by viewModels {
        AudioDeviceViewModelFactory(
            GetAudioDevicesUseCase(audioDeviceProvider),
            audioDeviceMonitor.observeDevicesChanged()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Valor inicial DYNAMIC (expuesto por el ViewModel) y, tras la primera
            // lectura de DataStore, el modo guardado. El cambio se aplica sin reiniciar.
            val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
            HiFiPlayerTheme(themeMode = themeMode) {
                AppRoot(
                    libraryViewModel = libraryViewModel,
                    playbackViewModel = playbackViewModel,
                    settingsViewModel = settingsViewModel,
                    audioDeviceViewModel = audioDeviceViewModel
                )
            }
        }
    }
}
