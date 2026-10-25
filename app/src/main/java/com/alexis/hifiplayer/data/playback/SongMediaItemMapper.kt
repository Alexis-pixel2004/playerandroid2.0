package com.alexis.hifiplayer.data.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.alexis.hifiplayer.domain.model.AudioCodec
import com.alexis.hifiplayer.domain.model.Song

/**
 * Mapeo reversible y mínimo entre [Song] (dominio) y [MediaItem] (Media3).
 *
 * Se usa para enviar la cola a la `MediaSession` del servicio y para reconstruirla
 * después desde el reproductor (que es la fuente de verdad). Media3 solo transporta
 * de forma nativa `mediaId`, la URI y los campos de [MediaMetadata]; el resto de los
 * campos de [Song] se guardan en `MediaMetadata.extras` para poder reconstruir el
 * elemento sin inventar datos.
 *
 * Honestidad: no se añade ningún campo técnico que no exista ya en [Song] ni se
 * reinterpretan valores; los `null` de `bitDepth`/`sampleRateHz`/`channelCount`/
 * `bitRate` se conservan como ausencia de clave (no como `0`).
 */

private const val EXTRA_DURATION_MS = "hifi.song.durationMs"
private const val EXTRA_DISPLAY_NAME = "hifi.song.displayName"
private const val EXTRA_FILE_EXTENSION = "hifi.song.fileExtension"
private const val EXTRA_SIZE_BYTES = "hifi.song.sizeBytes"
private const val EXTRA_MIME_TYPE = "hifi.song.mimeType"
private const val EXTRA_DATE_MODIFIED = "hifi.song.dateModified"
private const val EXTRA_BIT_DEPTH = "hifi.song.bitDepth"
private const val EXTRA_SAMPLE_RATE_HZ = "hifi.song.sampleRateHz"
private const val EXTRA_CHANNEL_COUNT = "hifi.song.channelCount"
private const val EXTRA_BIT_RATE = "hifi.song.bitRate"
private const val EXTRA_CODEC = "hifi.song.codec"

/** Convierte la canción de dominio en el [MediaItem] que viaja a la sesión. */
internal fun Song.toSessionMediaItem(): MediaItem {
    val extras = Bundle().apply {
        putLong(EXTRA_DURATION_MS, durationMs)
        putString(EXTRA_DISPLAY_NAME, displayName)
        putString(EXTRA_FILE_EXTENSION, fileExtension)
        putLong(EXTRA_SIZE_BYTES, sizeBytes)
        putString(EXTRA_MIME_TYPE, mimeType)
        putLong(EXTRA_DATE_MODIFIED, dateModified)
        // Solo se guardan los campos técnicos realmente conocidos; su ausencia
        // representa "desconocido" (null), no cero.
        bitDepth?.let { putInt(EXTRA_BIT_DEPTH, it) }
        sampleRateHz?.let { putInt(EXTRA_SAMPLE_RATE_HZ, it) }
        channelCount?.let { putInt(EXTRA_CHANNEL_COUNT, it) }
        bitRate?.let { putLong(EXTRA_BIT_RATE, it) }
        putString(EXTRA_CODEC, codec.name)
    }

    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setExtras(extras)
        .build()

    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(contentUri)
        .setMediaMetadata(metadata)
        .build()
}

/**
 * Reconstruye la canción de dominio desde el [MediaItem] que devuelve el reproductor.
 * Los campos no presentes vuelven a sus valores por defecto de [Song]; los técnicos
 * desconocidos se mantienen como `null`.
 */
internal fun MediaItem.toSessionSong(): Song {
    val extras = mediaMetadata.extras
    return Song(
        id = mediaId.toLongOrNull() ?: 0L,
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString().orEmpty(),
        album = mediaMetadata.albumTitle?.toString().orEmpty(),
        durationMs = extras?.getLong(EXTRA_DURATION_MS) ?: 0L,
        contentUri = localConfiguration?.uri?.toString().orEmpty(),
        displayName = extras?.getString(EXTRA_DISPLAY_NAME).orEmpty(),
        fileExtension = extras?.getString(EXTRA_FILE_EXTENSION).orEmpty(),
        sizeBytes = extras?.getLong(EXTRA_SIZE_BYTES) ?: 0L,
        mimeType = extras?.getString(EXTRA_MIME_TYPE).orEmpty(),
        dateModified = extras?.getLong(EXTRA_DATE_MODIFIED) ?: 0L,
        bitDepth = extras?.getIntOrNull(EXTRA_BIT_DEPTH),
        sampleRateHz = extras?.getIntOrNull(EXTRA_SAMPLE_RATE_HZ),
        channelCount = extras?.getIntOrNull(EXTRA_CHANNEL_COUNT),
        bitRate = extras?.getLongOrNull(EXTRA_BIT_RATE),
        codec = extras?.getString(EXTRA_CODEC)?.toAudioCodec() ?: AudioCodec.UNKNOWN
    )
}

/** Devuelve el entero solo si la clave existe; si no, `null` (desconocido). */
private fun Bundle.getIntOrNull(key: String): Int? = if (containsKey(key)) getInt(key) else null

/** Devuelve el long solo si la clave existe; si no, `null` (desconocido). */
private fun Bundle.getLongOrNull(key: String): Long? = if (containsKey(key)) getLong(key) else null

/** Nombre de [AudioCodec] almacenado en el bundle; cualquier valor desconocido cae en [AudioCodec.UNKNOWN]. */
private fun String.toAudioCodec(): AudioCodec =
    AudioCodec.entries.firstOrNull { it.name == this } ?: AudioCodec.UNKNOWN
