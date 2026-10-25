package com.alexis.hifiplayer.ui.audioddevice

import com.alexis.hifiplayer.domain.model.AudioCodec
import com.alexis.hifiplayer.domain.model.AudioDevice
import com.alexis.hifiplayer.domain.model.AudioDeviceCapabilities
import com.alexis.hifiplayer.domain.model.AudioDeviceType
import com.alexis.hifiplayer.domain.model.BitPerfectStatus
import com.alexis.hifiplayer.domain.model.CapabilityState
import com.alexis.hifiplayer.domain.model.AudioTrackConfig
import com.alexis.hifiplayer.domain.model.DecoderInputFormat
import com.alexis.hifiplayer.domain.model.PcmEncoding
import com.alexis.hifiplayer.domain.model.SelectedTrackFormat
import com.alexis.hifiplayer.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica el constructor puro del diagnóstico de ruta y la clasificación conservadora
 * de bit-perfect, sin dependencias de Android (JUnit puro).
 */
class AudioPathDiagnosticsTest {

    private val usbDac = AudioDevice(
        id = "usb-dac",
        name = "DAC USB",
        type = AudioDeviceType.USB_DAC,
        isConnected = true,
        isRouted = true
    )

    private val usbDacCapabilities = AudioDeviceCapabilities(
        supportedSampleRates = listOf(44100, 48000, 96000),
        supportedEncodings = listOf(PcmEncoding.PCM_24),
        supportedChannelCounts = listOf(2),
        capabilityState = CapabilityState.KNOWN
    )

    private fun routedState(
        device: AudioDevice? = usbDac,
        capabilities: AudioDeviceCapabilities? = usbDacCapabilities
    ): AudioDeviceUiState = buildAudioDeviceUiState(
        availableDevices = listOfNotNull(device),
        routedDevice = device,
        routedCapabilities = capabilities
    )

    @Test
    fun losslessSourcePreservesFieldsAndUnknownsStayNull() {
        val song = Song(
            id = 1L,
            title = "Pista",
            codec = AudioCodec.FLAC,
            sampleRateHz = 96000,
            bitDepth = 24
            // channelCount y bitRate quedan en null a propósito.
        )

        val diagnostics = buildAudioPathDiagnostics(song, routedState())

        assertTrue(diagnostics.hasSource)
        assertEquals(AudioCodec.FLAC, diagnostics.sourceCodec)
        assertEquals(true, diagnostics.sourceIsLossless)
        assertEquals(96000, diagnostics.sourceSampleRateHz)
        assertEquals(24, diagnostics.sourceBitDepth)
        assertNull(diagnostics.sourceChannelCount)
        assertNull(diagnostics.sourceBitRate)
    }

    @Test
    fun declaredCapabilitiesDoNotConfirmBitPerfect() {
        // Aunque Android declare capacidades y la ruta, no hay evidencia del recorrido
        // completo de la señal: nunca BIT_PERFECT.
        val diagnostics = buildAudioPathDiagnostics(Song(codec = AudioCodec.FLAC), routedState())

        assertNotEquals(BitPerfectStatus.BIT_PERFECT, diagnostics.bitPerfectStatus)
        assertTrue(diagnostics.reasons.isNotEmpty())
    }

    @Test
    fun lossySourceIsNeverBitPerfectNorBlamesTheRoute() {
        val diagnostics = buildAudioPathDiagnostics(Song(codec = AudioCodec.MP3), routedState())

        assertNotEquals(BitPerfectStatus.BIT_PERFECT, diagnostics.bitPerfectStatus)
        assertEquals(BitPerfectStatus.CONVERTED, diagnostics.bitPerfectStatus)
        // La naturaleza de la fuente no debe leerse como incompatibilidad del DAC/ruta.
        assertEquals("No confirmado", diagnostics.bitPerfectStatus.displayLabel())
    }

    @Test
    fun noRoutedDeviceIsNeverBitPerfect() {
        val diagnostics = buildAudioPathDiagnostics(
            Song(codec = AudioCodec.FLAC),
            routedState(device = null, capabilities = null)
        )

        assertNotEquals(BitPerfectStatus.BIT_PERFECT, diagnostics.bitPerfectStatus)
    }

    @Test
    fun absentSongKeepsEverySourceValueUnknown() {
        val diagnostics = buildAudioPathDiagnostics(null, routedState())

        assertFalse(diagnostics.hasSource)
        assertEquals(AudioCodec.UNKNOWN, diagnostics.sourceCodec)
        assertNull(diagnostics.sourceIsLossless)
        assertNull(diagnostics.sourceSampleRateHz)
        assertNull(diagnostics.sourceBitDepth)
        assertNull(diagnostics.sourceChannelCount)
        assertNull(diagnostics.sourceBitRate)
        assertNotEquals(BitPerfectStatus.BIT_PERFECT, diagnostics.bitPerfectStatus)
    }

    @Test
    fun sourceSummaryReportsKnownFields() {
        val song = Song(
            codec = AudioCodec.FLAC,
            sampleRateHz = 44100,
            bitDepth = 16,
            channelCount = 2,
            bitRate = 900_000L
        )

        val summary = buildAudioPathDiagnostics(song, routedState()).sourceSummary()

        assertTrue(summary.contains("FLAC"))
        assertTrue(summary.contains("44100 Hz"))
        assertTrue(summary.contains("16 bits"))
        assertTrue(summary.contains("2 canales"))
        assertTrue(summary.contains("900 kbps"))
    }

    @Test
    fun sourceSummaryNeverInventsZeroForUnknownValues() {
        val song = Song(codec = AudioCodec.UNKNOWN)
        val summary = buildAudioPathDiagnostics(song, routedState()).sourceSummary()

        assertTrue(summary.contains("muestreo desconocido"))
        assertTrue(summary.contains("profundidad desconocida"))
        assertFalse(summary.contains("0 Hz"))
        assertFalse(summary.contains("0 bits"))
    }

    @Test
    fun sourceSummaryTreatsZeroAndNegativeAsUnknown() {
        val zeroed = Song(
            codec = AudioCodec.FLAC,
            sampleRateHz = 0,
            bitDepth = 0,
            channelCount = 0,
            bitRate = 0L
        )
        val zeroSummary = buildAudioPathDiagnostics(zeroed, routedState()).sourceSummary()

        assertTrue(zeroSummary.contains("muestreo desconocido"))
        assertTrue(zeroSummary.contains("profundidad desconocida"))
        assertTrue(zeroSummary.contains("canales desconocidos"))
        assertFalse(zeroSummary.contains("0 Hz"))
        assertFalse(zeroSummary.contains("0 bits"))
        assertFalse(zeroSummary.contains("0 canales"))
        assertFalse(zeroSummary.contains("0 bps"))

        val negative = Song(
            codec = AudioCodec.FLAC,
            sampleRateHz = -1,
            bitDepth = -1,
            channelCount = -1,
            bitRate = -1L
        )
        val negativeSummary = buildAudioPathDiagnostics(negative, routedState()).sourceSummary()

        assertTrue(negativeSummary.contains("muestreo desconocido"))
        assertTrue(negativeSummary.contains("profundidad desconocida"))
        assertTrue(negativeSummary.contains("canales desconocidos"))
        assertFalse(negativeSummary.contains("-1 Hz"))
        assertFalse(negativeSummary.contains("-1 bits"))
        assertFalse(negativeSummary.contains("-1 canales"))
        assertFalse(negativeSummary.contains("-1 bps"))
    }

    @Test
    fun unknownLosslessSourceIsNotAssumedLosslessNorBitPerfect() {
        val diagnostics = buildAudioPathDiagnostics(Song(codec = AudioCodec.OGG), routedState())

        assertNull(diagnostics.sourceIsLossless)
        assertNotEquals(BitPerfectStatus.BIT_PERFECT, diagnostics.bitPerfectStatus)
        assertEquals("No confirmado", diagnostics.bitPerfectStatus.displayLabel())
    }

    @Test
    fun sourceSummaryOfAbsentSongIsNotAvailable() {
        val summary = buildAudioPathDiagnostics(null, routedState()).sourceSummary()

        assertEquals("No disponible (sin pista en reproducción)", summary)
    }

    @Test
    fun displayLabelsAvoidClaimingConfirmacion() {
        // Etapa 1: sin evidencia de la ruta/DAC, toda etiqueta de estado es conservadora.
        assertEquals("No confirmado", BitPerfectStatus.BIT_PERFECT.displayLabel())
        assertEquals("No confirmado", BitPerfectStatus.LOSSLESS.displayLabel())
        assertEquals("No confirmado", BitPerfectStatus.CONVERTED.displayLabel())
        assertEquals("No confirmado", BitPerfectStatus.UNAVAILABLE.displayLabel())
        assertEquals("No identificado", AudioCodec.UNKNOWN.displayLabel())
    }

    @Test
    fun formatsBitRateConservatively() {
        assertEquals("900 kbps", formatBitRate(900_000L))
        assertEquals("500 bps", formatBitRate(500L))
    }

    // --- Etapa 2a: formato seleccionado por el reproductor ---------------------------

    @Test
    fun selectedFormatIsExposedSeparatelyFromSource() {
        val source = Song(codec = AudioCodec.FLAC, sampleRateHz = 96000, bitDepth = 24)
        val selected = SelectedTrackFormat.Single(
            codec = AudioCodec.FLAC,
            mimeType = "audio/flac",
            sampleRateHz = 96000,
            channelCount = 2
            // bitDepth/bitRate desconocidos: en FLAC comprimido no se declaran.
        )

        val diagnostics = buildAudioPathDiagnostics(source, routedState(), selected)

        // El formato de origen y el seleccionado son campos independientes.
        assertEquals(AudioCodec.FLAC, diagnostics.sourceCodec)
        assertEquals(24, diagnostics.sourceBitDepth)
        assertEquals(selected, diagnostics.selectedFormat)
        assertEquals(2, (diagnostics.selectedFormat as SelectedTrackFormat.Single).channelCount)
        assertNull((diagnostics.selectedFormat as SelectedTrackFormat.Single).bitDepth)
    }

    @Test
    fun selectedSummaryReportsKnownFields() {
        val selected = SelectedTrackFormat.Single(
            codec = AudioCodec.FLAC,
            mimeType = "audio/flac",
            sampleRateHz = 96000,
            channelCount = 2,
            bitDepth = 24,
            bitRate = 4_600_000L
        )

        val diagnostics = buildAudioPathDiagnostics(
            Song(codec = AudioCodec.FLAC),
            routedState(),
            selected
        )
        val summary = diagnostics.selectedSummary()

        assertTrue(summary.contains("FLAC"))
        assertTrue(summary.contains("96000 Hz"))
        assertTrue(summary.contains("2 canales"))
        assertTrue(summary.contains("24 bits"))
        assertTrue(summary.contains("4600 kbps"))
    }

    @Test
    fun selectedSummaryTreatsZeroAndNegativeAsUnknown() {
        val zeroed = SelectedTrackFormat.Single(
            codec = AudioCodec.FLAC,
            sampleRateHz = 0,
            channelCount = 0,
            bitDepth = 0,
            bitRate = 0L
        )
        val zeroSummary =
            buildAudioPathDiagnostics(Song(codec = AudioCodec.FLAC), routedState(), zeroed)
                .selectedSummary()

        assertTrue(zeroSummary.contains("muestreo desconocido"))
        assertTrue(zeroSummary.contains("canales desconocidos"))
        assertTrue(zeroSummary.contains("profundidad desconocida"))
        assertFalse(zeroSummary.contains("0 Hz"))
        assertFalse(zeroSummary.contains("0 canales"))
        assertFalse(zeroSummary.contains("0 bits"))
        assertFalse(zeroSummary.contains("0 bps"))

        val negative = SelectedTrackFormat.Single(
            codec = AudioCodec.FLAC,
            sampleRateHz = -1,
            channelCount = -1,
            bitDepth = -1,
            bitRate = -1L
        )
        val negativeSummary =
            buildAudioPathDiagnostics(Song(codec = AudioCodec.FLAC), routedState(), negative)
                .selectedSummary()

        assertTrue(negativeSummary.contains("muestreo desconocido"))
        assertTrue(negativeSummary.contains("canales desconocidos"))
        assertTrue(negativeSummary.contains("profundidad desconocida"))
        assertFalse(negativeSummary.contains("-1 Hz"))
        assertFalse(negativeSummary.contains("-1 canales"))
        assertFalse(negativeSummary.contains("-1 bits"))
        assertFalse(negativeSummary.contains("-1 bps"))
    }

    @Test
    fun selectedSummaryWithoutSelectionIsNotAvailable() {
        val withSong =
            buildAudioPathDiagnostics(Song(codec = AudioCodec.FLAC), routedState()).selectedSummary()
        assertEquals("No disponible (sin selección del reproductor)", withSong)

        val withoutSong = buildAudioPathDiagnostics(null, routedState()).selectedSummary()
        assertEquals("No disponible (sin pista en reproducción)", withoutSong)
    }

    @Test
    fun ambiguousSelectionIsReportedExplicitly() {
        val ambiguous = SelectedTrackFormat.Ambiguous(selectedTrackCount = 3)
        val summary =
            buildAudioPathDiagnostics(Song(codec = AudioCodec.FLAC), routedState(), ambiguous)
                .selectedSummary()

        assertTrue(summary.contains("Ambiguo"))
        assertTrue(summary.contains("3"))
    }

    @Test
    fun selectedFormatDoesNotConfirmBitPerfect() {
        val selected = SelectedTrackFormat.Single(
            codec = AudioCodec.FLAC,
            sampleRateHz = 96000,
            channelCount = 2,
            bitDepth = 24
        )

        val diagnostics = buildAudioPathDiagnostics(
            Song(codec = AudioCodec.FLAC),
            routedState(),
            selected
        )

        // Saber la pista seleccionada no aporta evidencia del recorrido hasta el DAC.
        assertNotEquals(BitPerfectStatus.BIT_PERFECT, diagnostics.bitPerfectStatus)
        assertEquals("No confirmado", diagnostics.bitPerfectStatus.displayLabel())
        assertTrue(SELECTED_FORMAT_DISCLAIMER.contains("DAC"))
    }

    // --- Etapa 2b: formato de entrada al decodificador y configuración AudioTrack ---

    @Test
    fun decoderInputSummaryReportsKnownFields() {
        val input = DecoderInputFormat(
            codec = AudioCodec.FLAC,
            mimeType = "audio/flac",
            sampleRateHz = 96000,
            channelCount = 2,
            bitDepth = 24,
            bitRate = 4_600_000L
        )

        val diagnostics = buildAudioPathDiagnostics(
            Song(codec = AudioCodec.FLAC),
            routedState(),
            selectedFormat = null,
            decoderInputFormat = input,
            audioTrackConfig = null
        )
        val summary = diagnostics.decoderInputSummary()

        assertTrue(summary.contains("FLAC"))
        assertTrue(summary.contains("audio/flac"))
        assertTrue(summary.contains("96000 Hz"))
        assertTrue(summary.contains("2 canales"))
        assertTrue(summary.contains("24 bits"))
        assertTrue(summary.contains("4600 kbps"))
    }

    @Test
    fun decoderInputSummaryTreatsZeroAndNegativeAsUnknown() {
        val zeroed = DecoderInputFormat(
            codec = AudioCodec.FLAC,
            mimeType = "audio/flac",
            sampleRateHz = 0,
            channelCount = 0,
            bitDepth = 0,
            bitRate = 0L
        )
        val zeroSummary =
            buildAudioPathDiagnostics(Song(codec = AudioCodec.FLAC), routedState(),
                decoderInputFormat = zeroed).decoderInputSummary()

        assertTrue(zeroSummary.contains("muestreo desconocido"))
        assertTrue(zeroSummary.contains("canales desconocidos"))
        assertTrue(zeroSummary.contains("profundidad desconocida"))
        assertFalse(zeroSummary.contains("0 Hz"))
        assertFalse(zeroSummary.contains("0 canales"))
        assertFalse(zeroSummary.contains("0 bits"))
        assertFalse(zeroSummary.contains("0 bps"))

        val negative = DecoderInputFormat(
            codec = AudioCodec.FLAC,
            mimeType = "audio/flac",
            sampleRateHz = -1,
            channelCount = -1,
            bitDepth = -1,
            bitRate = -1L
        )
        val negativeSummary =
            buildAudioPathDiagnostics(Song(codec = AudioCodec.FLAC), routedState(),
                decoderInputFormat = negative).decoderInputSummary()

        assertTrue(negativeSummary.contains("muestreo desconocido"))
        assertTrue(negativeSummary.contains("canales desconocidos"))
        assertTrue(negativeSummary.contains("profundidad desconocida"))
        assertFalse(negativeSummary.contains("-1 Hz"))
        assertFalse(negativeSummary.contains("-1 canales"))
        assertFalse(negativeSummary.contains("-1 bits"))
        assertFalse(negativeSummary.contains("-1 bps"))
    }

    @Test
    fun decoderInputSummaryWithoutDataIsNotAvailable() {
        val withSong =
            buildAudioPathDiagnostics(Song(codec = AudioCodec.FLAC), routedState()).decoderInputSummary()
        assertEquals("No disponible (sin dato del decodificador)", withSong)

        val withoutSong = buildAudioPathDiagnostics(null, routedState()).decoderInputSummary()
        assertEquals("No disponible (sin pista en reproducción)", withoutSong)
    }

    @Test
    fun audioTrackConfigSummaryReportsKnownFields() {
        val config = AudioTrackConfig(
            encoding = PcmEncoding.PCM_24,
            sampleRateHz = 96000,
            channelCount = 2,
            bufferSizeBytes = 4096,
            tunneling = false,
            offload = false
        )

        val diagnostics = buildAudioPathDiagnostics(
            Song(codec = AudioCodec.FLAC),
            routedState(),
            selectedFormat = null,
            decoderInputFormat = null,
            audioTrackConfig = config
        )
        val summary = diagnostics.audioTrackConfigSummary()

        assertTrue(summary.contains("PCM 24 bits"))
        assertTrue(summary.contains("96000 Hz"))
        assertTrue(summary.contains("2 canales"))
        assertTrue(summary.contains("buffer 4096 B"))
        assertFalse(summary.contains("túnel"))
        assertFalse(summary.contains("offload"))
    }

    @Test
    fun audioTrackConfigSummaryIncludesTunnelingAndOffload() {
        val config = AudioTrackConfig(
            encoding = PcmEncoding.PCM_16,
            sampleRateHz = 48000,
            channelCount = 2,
            bufferSizeBytes = 8192,
            tunneling = true,
            offload = true
        )

        val summary = buildAudioPathDiagnostics(
            Song(codec = AudioCodec.FLAC),
            routedState(),
            audioTrackConfig = config
        ).audioTrackConfigSummary()

        assertTrue(summary.contains("túnel"))
        assertTrue(summary.contains("offload"))
    }

    @Test
    fun audioTrackConfigSummaryTreatsZeroAndNegativeAsUnknown() {
        val zeroed = AudioTrackConfig(
            encoding = PcmEncoding.PCM_16,
            sampleRateHz = 0,
            channelCount = 0,
            bufferSizeBytes = 0,
            tunneling = false,
            offload = false
        )
        val zeroSummary =
            buildAudioPathDiagnostics(Song(codec = AudioCodec.FLAC), routedState(),
                audioTrackConfig = zeroed).audioTrackConfigSummary()

        assertTrue(zeroSummary.contains("muestreo desconocido"))
        assertTrue(zeroSummary.contains("canales desconocidos"))
        assertFalse(zeroSummary.contains("0 Hz"))
        assertFalse(zeroSummary.contains("0 canales"))
        assertFalse(zeroSummary.contains("0 B"))

        val negative = AudioTrackConfig(
            encoding = PcmEncoding.PCM_16,
            sampleRateHz = -1,
            channelCount = -1,
            bufferSizeBytes = -1,
            tunneling = false,
            offload = false
        )
        val negativeSummary =
            buildAudioPathDiagnostics(Song(codec = AudioCodec.FLAC), routedState(),
                audioTrackConfig = negative).audioTrackConfigSummary()

        assertTrue(negativeSummary.contains("muestreo desconocido"))
        assertTrue(negativeSummary.contains("canales desconocidos"))
        assertFalse(negativeSummary.contains("-1 Hz"))
        assertFalse(negativeSummary.contains("-1 canales"))
        assertFalse(negativeSummary.contains("-1 B"))
    }

    @Test
    fun audioTrackConfigSummaryWithoutDataIsNotAvailable() {
        val withSong =
            buildAudioPathDiagnostics(Song(codec = AudioCodec.FLAC), routedState()).audioTrackConfigSummary()
        assertEquals("No disponible (sin configuración de AudioTrack)", withSong)

        val withoutSong = buildAudioPathDiagnostics(null, routedState()).audioTrackConfigSummary()
        assertEquals("No disponible (sin pista en reproducción)", withoutSong)
    }

    @Test
    fun decoderInputAndAudioTrackAreIndependentFromSourceAndSelected() {
        val source = Song(codec = AudioCodec.FLAC, sampleRateHz = 96000, bitDepth = 24)
        val selected = SelectedTrackFormat.Single(
            codec = AudioCodec.FLAC,
            mimeType = "audio/flac",
            sampleRateHz = 96000,
            channelCount = 2
        )
        val input = DecoderInputFormat(
            codec = AudioCodec.FLAC,
            mimeType = "audio/flac",
            sampleRateHz = 96000,
            channelCount = 2,
            bitDepth = 24,
            bitRate = 4_600_000L
        )
        val config = AudioTrackConfig(
            encoding = PcmEncoding.PCM_24,
            sampleRateHz = 96000,
            channelCount = 2,
            bufferSizeBytes = 4096
        )

        val diagnostics = buildAudioPathDiagnostics(
            source, routedState(),
            selectedFormat = selected,
            decoderInputFormat = input,
            audioTrackConfig = config
        )

        // Todos los campos son independientes y coexisten
        assertEquals(AudioCodec.FLAC, diagnostics.sourceCodec)
        assertEquals(selected, diagnostics.selectedFormat)
        assertEquals(input, diagnostics.decoderInputFormat)
        assertEquals(config, diagnostics.audioTrackConfig)
    }

    @Test
    fun newDiagnosticsDoNotConfirmBitPerfect() {
        val input = DecoderInputFormat(
            codec = AudioCodec.FLAC,
            sampleRateHz = 96000,
            channelCount = 2,
            bitDepth = 24
        )
        val config = AudioTrackConfig(
            encoding = PcmEncoding.PCM_24,
            sampleRateHz = 96000,
            channelCount = 2
        )

        val diagnostics = buildAudioPathDiagnostics(
            Song(codec = AudioCodec.FLAC),
            routedState(),
            decoderInputFormat = input,
            audioTrackConfig = config
        )

        // Conocer el formato de entrada al decodificador y la config del AudioTrack
        // no prueba el recorrido completo hasta el DAC.
        assertNotEquals(BitPerfectStatus.BIT_PERFECT, diagnostics.bitPerfectStatus)
        assertEquals("No confirmado", diagnostics.bitPerfectStatus.displayLabel())
        assertTrue(DECODER_INPUT_DISCLAIMER.contains("DAC"))
        assertTrue(AUDIO_TRACK_CONFIG_DISCLAIMER.contains("DAC"))
    }
}
