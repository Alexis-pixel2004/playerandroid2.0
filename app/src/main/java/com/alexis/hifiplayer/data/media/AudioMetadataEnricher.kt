package com.alexis.hifiplayer.data.media

import android.content.Context
import android.util.LruCache
import com.alexis.hifiplayer.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Completa los campos técnicos de una lista de [Song] a partir del **archivo original**
 * del audio, delegando en [AudioStreamMetadataExtractor].
 *
 * Este enriquecedor pertenece a `data/`: el dominio (`Song`) no conoce a MediaExtractor
 * ni a MediaMetadataRetriever. La procedencia de cada dato (`AudioStreamMetadata` con
 * su `MetadataSource`) se conserva en la caché, pero **no** se copia a `Song`, porque
 * hoy ningún consumidor de `Song` la necesita; el modelo de dominio sigue limpio.
 *
 * Reglas:
 * - Un valor desconocido permanece como `null`. **Nunca** se convierte a `0` ni se
 *   inventa.
 * - [AudioStreamMetadataResult.Unsupported] y [AudioStreamMetadataResult.Failed] se
 *   tratan igual que "sin metadatos": la canción se devuelve tal cual, sigue
 *   apareciendo en la biblioteca y puede reproducirse.
 * - La extracción se ejecuta en [Dispatchers.IO], con concurrencia acotada.
 * - La caché es un `LruCache` acotado por número de entradas (keyed por URI), de modo
 *   que no se reanaliza indefinidamente la misma canción durante la sesión.
 * - No reproduce audio ni toca el motor de reproducción.
 */
class AudioMetadataEnricher(
    context: Context,
    private val extractor: AudioStreamMetadataExtractor = AudioStreamMetadataExtractor(context)
) {

    private val cache = LruCache<String, AudioStreamMetadataResult>(MAX_CACHED_ENTRIES)

    /**
     * Devuelve una copia por canción con `bitDepth`, `sampleRateHz`, `channelCount` y
     * `bitRate` rellenados **solo** cuando el archivo los expone; el resto de campos de
     * [Song] no se toca.
     *
     * No lanza por fallos de extracción (el extractor los devuelve tipados). Respeta la
     * cancelación de coroutines.
     */
    suspend fun enrich(songs: List<Song>): List<Song> {
        if (songs.isEmpty()) return emptyList()
        val permits = Semaphore(MAX_CONCURRENT_ANALYSES)
        return coroutineScope {
            songs.map { song ->
                async(Dispatchers.IO) {
                    permits.withPermit { enrichOne(song) }
                }
            }.awaitAll()
        }
    }

    private suspend fun enrichOne(song: Song): Song {
        if (song.contentUri.isBlank()) return song

        val result = cache.get(song.contentUri) ?: run {
            val fresh = extractor.extract(
                contentUri = song.contentUri,
                mimeTypeHint = song.mimeType,
                fileExtension = song.fileExtension
            )
            cache.put(song.contentUri, fresh)
            fresh
        }

        return when (result) {
            is AudioStreamMetadataResult.Available -> song.withStreamMetadata(result.metadata)
            is AudioStreamMetadataResult.Unsupported,
            is AudioStreamMetadataResult.Failed -> song
        }
    }

    /**
     * Copia a [Song] únicamente los campos que el archivo expone como conocidos
     * (`null` = desconocido, se mantiene). No se rellena `codec`: eso corresponde al
     * mapeo de MIME y queda fuera de este paso.
     */
    private fun Song.withStreamMetadata(metadata: AudioStreamMetadata): Song = copy(
        bitDepth = metadata.bitDepth,
        sampleRateHz = metadata.sampleRateHz,
        channelCount = metadata.channelCount,
        bitRate = metadata.bitRate
    )

    private companion object {
        /** Número máximo de resultados cacheados (entradas, no bytes). */
        const val MAX_CACHED_ENTRIES = 256

        /** Análisis de archivos simultáneos para no saturar el almacenamiento. */
        const val MAX_CONCURRENT_ANALYSES = 4
    }
}
