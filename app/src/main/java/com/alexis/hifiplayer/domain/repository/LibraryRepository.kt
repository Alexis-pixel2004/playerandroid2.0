package com.alexis.hifiplayer.domain.repository

import com.alexis.hifiplayer.domain.model.Song
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun observeSongs(): Flow<List<Song>>
    suspend fun getSongs(): List<Song>
}
