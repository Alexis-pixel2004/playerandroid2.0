package com.alexis.hifiplayer.ui.audioddevice

import com.alexis.hifiplayer.domain.model.AudioDevice
import com.alexis.hifiplayer.domain.model.AudioDeviceCapabilities
import com.alexis.hifiplayer.domain.model.AudioDeviceType
import com.alexis.hifiplayer.domain.model.PcmEncoding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica el constructor puro de estado de UI y los formateadores, sin dependencias
 * de Android (JUnit puro).
 */
class AudioDeviceUiStateBuilderTest {

    @Test
    fun routedDeviceWithCapabilitiesPopulatesState() {
        val device = AudioDevice(
            id = "1",
            name = "DAC USB",
            type = AudioDeviceType.USB_DAC,
            isConnected = true,
            isRouted = true
        )
        val caps = AudioDeviceCapabilities(
            supportedSampleRates = listOf(44100, 96000),
            supportedChannelCounts = listOf(2),
            supportedEncodings = listOf(PcmEncoding.PCM_16, PcmEncoding.PCM_24)
        )

        val state = buildAudioDeviceUiState(listOf(device), device, caps)

        assertFalse(state.isLoading)
        assertTrue(state.hasRoutedDevice)
        assertEquals(device, state.routedDevice)
        assertEquals(caps, state.capabilities)
        assertEquals(1, state.availableDevices.size)
        assertNull(state.errorMessage)
    }

    @Test
    fun noRoutedDeviceMarksUncertainty() {
        val state = buildAudioDeviceUiState(emptyList(), null, null)

        assertNull(state.routedDevice)
        assertNull(state.capabilities)
        assertFalse(state.hasRoutedDevice)
        assertTrue(state.uncertainties.isNotEmpty())
    }

    @Test
    fun unknownValuesAreNotReportedAsZero() {
        val state = buildAudioDeviceUiState(emptyList(), null, null)

        // Un estado desconocido debe seguir siendo desconocido: no se rellena con 0.
        assertNull(state.routedDevice?.id)
        assertEquals("No declaradas por Android", formatSampleRates(state.capabilities?.supportedSampleRates.orEmpty()))
    }

    @Test
    fun formatsDeclaredCapabilities() {
        assertEquals("44100 Hz, 96000 Hz", formatSampleRates(listOf(96000, 44100)))
        assertEquals("2", formatChannelCounts(listOf(2)))
        assertEquals("PCM 16 bits, PCM 24 bits", formatEncodings(listOf(PcmEncoding.PCM_16, PcmEncoding.PCM_24)))
    }

    @Test
    fun formatsEmptyCapabilitiesAsNotDeclared() {
        assertEquals("No declaradas por Android", formatSampleRates(emptyList()))
        assertEquals("No declarado por Android", formatChannelCounts(emptyList()))
        assertEquals("No declaradas por Android", formatEncodings(emptyList()))
    }

    @Test
    fun displayNameFallsBackToType() {
        val unnamed = AudioDevice(name = "", type = AudioDeviceType.USB_DAC)
        assertEquals("USB DAC", unnamed.displayName())

        val named = AudioDevice(name = "Mi DAC", type = AudioDeviceType.USB_DAC)
        assertEquals("Mi DAC", named.displayName())
    }
}
