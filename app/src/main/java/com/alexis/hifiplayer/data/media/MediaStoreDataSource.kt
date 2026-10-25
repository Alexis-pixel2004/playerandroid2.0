
package com.alexis.hifiplayer.data.media

import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import com.alexis.hifiplayer.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreDataSource(private val context: Context) {

    suspend fun querySongs(): List<Song> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_MODIFIED
        )

        val selection = "${MediaStore.Audio.Media.DURATION} > 0"
        var cursor: Cursor? = null
        val list = mutableListOf<Song>()

        try {
            cursor = resolver.query(
                uri,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )

            cursor?.let { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dispCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val modCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        .buildUpon()
                        .appendPath(id.toString())
                        .build()
                        .toString()

                    val displayName = c.getString(dispCol) ?: ""
                    val extension = displayName
                        .substringAfterLast('.', "")
                        .lowercase()
                    val mimeType = c.getString(mimeCol) ?: ""
                    // MIME primero (lo declara la plataforma); extensión solo como respaldo.
                    val codec = AudioCodecMapper.resolve(mimeType, extension)

                    list.add(
                        Song(
                            id = id,
                            title = c.getString(titleCol) ?: "",
                            artist = c.getString(artistCol) ?: "",
                            album = c.getString(albumCol) ?: "",
                            durationMs = c.getLong(durCol),
                            contentUri = contentUri,
                            displayName = displayName,
                            fileExtension = extension,
                            sizeBytes = c.getLong(sizeCol),
                            mimeType = mimeType,
                            dateModified = c.getLong(modCol),
                            codec = codec
                        )
                    )
                }
            }
        } catch (e: Exception) { android.util.Log.e("MediaStoreDataSource", "Error querying audio", e); throw e } finally {
            cursor?.close()
        }

        list
    }
}

