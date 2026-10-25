package com.alexis.hifiplayer.data.device

import android.media.AudioFormat
import com.alexis.hifiplayer.domain.model.PcmEncoding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Verifica el mapeo de codificaciones Android -> dominio con constantes con nombre.
 * No usa Android en tiempo de ejecución: las constantes `AudioFormat.ENCODING_*` se
 * inlinean en compilación.
 */
class AudioEncodingMapperTest {

    @Test
    fun mapsRepresentablePcmEncodings() {
        assertEquals(PcmEncoding.PCM_16, mapAndroidEncodingToPcmEncoding(AudioFormat.ENCODING_PCM_16BIT))
        assertEquals(PcmEncoding.PCM_24, mapAndroidEncodingToPcmEncoding(AudioFormat.ENCODING_PCM_24BIT_PACKED))
        assertEquals(PcmEncoding.PCM_32, mapAndroidEncodingToPcmEncoding(AudioFormat.ENCODING_PCM_32BIT))
        assertEquals(PcmEncoding.FLOAT, mapAndroidEncodingToPcmEncoding(AudioFormat.ENCODING_PCM_FLOAT))
    }

    @Test
    fun defaultEncodingIsNotPcm16() {
        // Regresión: ENCODING_DEFAULT (=1) no debe confundirse con ENCODING_PCM_16BIT (=2).
        assertNull(mapAndroidEncodingToPcmEncoding(AudioFormat.ENCODING_DEFAULT))
    }

    @Test
    fun unrepresentableEncodingsReturnNull() {
        assertNull(mapAndroidEncodingToPcmEncoding(AudioFormat.ENCODING_INVALID))
        assertNull(mapAndroidEncodingToPcmEncoding(AudioFormat.ENCODING_PCM_8BIT))
    }
}
