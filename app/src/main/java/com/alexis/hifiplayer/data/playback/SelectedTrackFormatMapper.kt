package com.alexis.hifiplayer.data.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import com.alexis.hifiplayer.data.media.AudioCodecMapper
import com.alexis.hifiplayer.domain.model.SelectedTrackFormat

/**
 * Traduce las pistas de Media3 ([Tracks]) al modelo de dominio [SelectedTrackFormat].
 *
 * Es Android/Media3-específico y vive en `data/`: el dominio no conoce `Tracks` ni
 * `Format`. No reproduce audio ni toca el motor; solo lee el estado de selección que
 * ExoPlayer ya publica mediante la API estable `Player.getCurrentTracks()`.
 *
 * Honestidad de los datos:
 * - [Format] describe la muestra **tal como la declara el contenedor** (comprimida en
 *   FLAC/ALAC/MP3/AAC/OGG). No es el PCM decodificado ni el formato del DAC.
 * - El centinela `Format.NO_VALUE` (`-1`), `0` y los negativos se traducen a `null`
 *   (desconocido): nunca se presentan como un valor real.
 *
 * Ambigüedad: si más de una pista de audio está seleccionada a la vez, devuelve
 * [SelectedTrackFormat.Ambiguous] en lugar de elegir una arbitrariamente.
 *
 * @return `null` si no hay ninguna pista de audio seleccionada (o [tracks] es `null`).
 */
internal fun mapSelectedTrackFormat(tracks: Tracks?): SelectedTrackFormat? {
    if (tracks == null) return null

    val selectedAudioFormats = mutableListOf<Format>()
    for (group in tracks.groups) {
        if (group.type != C.TRACK_TYPE_AUDIO) continue
        for (index in 0 until group.length) {
            if (group.isTrackSelected(index)) {
                selectedAudioFormats.add(group.getTrackFormat(index))
            }
        }
    }

    return when (selectedAudioFormats.size) {
        0 -> null
        1 -> selectedAudioFormats[0].toSelectedTrackFormat()
        else -> SelectedTrackFormat.Ambiguous(selectedTrackCount = selectedAudioFormats.size)
    }
}

/** Convierte un [Format] seleccionado a [SelectedTrackFormat.Single], saneando desconocidos. */
private fun Format.toSelectedTrackFormat(): SelectedTrackFormat.Single =
    SelectedTrackFormat.Single(
        codec = AudioCodecMapper.fromMime(sampleMimeType),
        mimeType = sampleMimeType?.takeIf { it.isNotBlank() },
        sampleRateHz = sampleRate.takeIf { it > 0 },
        channelCount = channelCount.takeIf { it > 0 },
        bitDepth = pcmEncodingToBitDepth(pcmEncoding),
        bitRate = bitrate.takeIf { it > 0 }?.toLong()
    )

/**
 * Traduce un `C.ENCODING_PCM_*` de Media3 a profundidad de bits. Devuelve `null` para
 * cualquier otro valor (incluido `Format.NO_VALUE`): en formatos comprimidos el
 * encoding PCM no está declarado, así que la profundidad queda desconocida.
 */
internal fun pcmEncodingToBitDepth(encoding: Int): Int? = when (encoding) {
    C.ENCODING_PCM_8BIT -> 8
    C.ENCODING_PCM_16BIT -> 16
    C.ENCODING_PCM_24BIT -> 24
    C.ENCODING_PCM_32BIT -> 32
    C.ENCODING_PCM_FLOAT -> 32
    else -> null
}
