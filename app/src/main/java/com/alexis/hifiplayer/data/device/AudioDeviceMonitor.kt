package com.alexis.hifiplayer.data.device

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.alexis.hifiplayer.domain.model.AudioDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class AudioDeviceMonitor(
    private val context: Context,
    private val audioDeviceProvider: AndroidAudioDeviceProvider
) {

    private val audioManager: AudioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun observeDevicesChanged(): Flow<Unit> = callbackFlow {
        send(Unit)
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                trySend(Unit)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                trySend(Unit)
            }
        }
        audioManager.registerAudioDeviceCallback(callback, null)
        awaitClose { audioManager.unregisterAudioDeviceCallback(callback) }
    }

    fun observeAvailableDevices(): Flow<List<AudioDevice>> = callbackFlow {
        launch { send(audioDeviceProvider.getAvailableDevices()) }
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                launch { trySend(audioDeviceProvider.getAvailableDevices()) }
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                launch { trySend(audioDeviceProvider.getAvailableDevices()) }
            }
        }
        audioManager.registerAudioDeviceCallback(callback, null)
        awaitClose { audioManager.unregisterAudioDeviceCallback(callback) }
    }

    fun observeActiveDevice(): Flow<AudioDevice?> = callbackFlow {
        launch { send(audioDeviceProvider.getActiveDevice()) }
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                launch { trySend(audioDeviceProvider.getActiveDevice()) }
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                launch { trySend(audioDeviceProvider.getActiveDevice()) }
            }
        }
        audioManager.registerAudioDeviceCallback(callback, null)
        awaitClose { audioManager.unregisterAudioDeviceCallback(callback) }
    }
}
