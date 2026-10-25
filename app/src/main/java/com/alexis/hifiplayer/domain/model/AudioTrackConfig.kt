package com.alexis.hifiplayer.domain.model

/**
 * Configuración real del `AudioTrack` creada por ExoPlayer
 * (callback `AnalyticsListener.onAudioTrackInitialized` → `AudioSink.AudioTrackConfig`).
 *
 * Es lo que ExoPlayer **configura** en el AudioTrack del sistema; el mixer de Android
 * puede alterarlo después (resampleo, cambio de canal, etc.), por lo que **no** es
 * el formato efectivo recibido por el DAC.
 *
 * Convención de honestidad: un dato no disponible es `null`; nunca se sustituye
 * por `0`, por valores negativos ni por centinelas.
 *
 * Es agnóstico de Android: la capa `data/` traduce los tipos de Media3 a este modelo.
 */
data class AudioTrackConfig(
    /** Codificación PCM configurada (p. ej. PCM_16BIT, PCM_24BIT_PACKED, PCM_FLOAT). */
    val encoding: PcmEncoding = PcmEncoding.UNKNOWN,
    /** Frecuencia de muestreo configurada (Hz), o `null` si no positiva. */
    val sampleRateHz: Int? = null,
    /** Número de canales configurado (derivado de `channelConfig`/`channelMask`), o `null`. */
    val channelCount: Int? = null,
    /** Tamaño del buffer en bytes, o `null` si no positivo. */
    val bufferSizeBytes: Int? = null,
    /** Si el AudioTrack usa túnel (offload a DSP). */
    val tunneling: Boolean = false,
    /** Si el AudioTrack usa offload (reproducción descodificada en hardware). */
    val offload: Boolean = false
)