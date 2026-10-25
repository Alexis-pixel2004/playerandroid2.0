package com.alexis.hifiplayer.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.alexis.hifiplayer.data.media.AudioMetadataEnricher
import com.alexis.hifiplayer.data.media.MediaStoreDataSource
import com.alexis.hifiplayer.domain.model.Song
import com.alexis.hifiplayer.domain.repository.LibraryRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class MediaStoreLibraryRepository(
    private val context: Context,
    private val dataSource: MediaStoreDataSource = MediaStoreDataSource(context),
    private val metadataEnricher: AudioMetadataEnricher = AudioMetadataEnricher(context)
) : LibraryRepository {

    override fun observeSongs(): Flow<List<Song>> = callbackFlow {
        val resolver: ContentResolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                launch { trySend(metadataEnricher.enrich(dataSource.querySongs())) }
            }
        }
        launch { trySend(metadataEnricher.enrich(dataSource.querySongs())) }
        resolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        awaitClose {
            resolver.unregisterContentObserver(observer)
        }
    }

    override suspend fun getSongs(): List<Song> {
        return metadataEnricher.enrich(dataSource.querySongs())
    }
}
