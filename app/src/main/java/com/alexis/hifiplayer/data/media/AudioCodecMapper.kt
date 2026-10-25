package com.alexis.hifiplayer.data.media

import com.alexis.hifiplayer.domain.model.AudioCodec

/**
 * Mapeo **centralizado** de MIME y extensión a [AudioCodec].
 *
 * Se usa en dos puntos Android-específicos (`data/`):
 * - [MediaStoreDataSource], durante la consulta a MediaStore.
 * - [AudioStreamMetadataExtractor], durante el análisis del archivo.
 *
 * Criterios:
 * - El **MIME** tiene prioridad porque lo declara la plataforma a partir del archivo.
 * - La **extensión** solo actúa como respaldo cuando el MIME no identifica el códec.
 * - Ante información ambigua se devuelve [AudioCodec.UNKNOWN]; no se inventa un códec.
 *
 * Limitación de contenedores vs códec:
 * - [AudioCodec] no distingue el códec interno de un contenedor. `.m4a`/`audio/mp4`
 *   puede llevar AAC (con pérdida) o ALAC (sin pérdida); un `.ogg` puede llevar
 *   Vorbis/Opus (con pérdida) o FLAC (sin pérdida).
 * - Por eso: un MIME M4A/MP4 ambiguo → [AudioCodec.UNKNOWN]; OGG se representa como
 *   [AudioCodec.OGG] (contenedor) y su `isLossless` es `null`, nunca `false`.
 */
object AudioCodecMapper {

    /** Resuelve el códec a partir de un MIME (p. ej. `audio/flac`). */
    fun fromMime(mime: String?): AudioCodec = when (mime?.trim()?.lowercase()) {
        "audio/flac", "audio/x-flac" -> AudioCodec.FLAC
        "audio/x-wav", "audio/wav", "audio/wave", "audio/vnd.wave", "audio/raw" -> AudioCodec.WAV
        "audio/alac", "audio/x-alac" -> AudioCodec.ALAC
        "audio/x-aiff", "audio/aiff", "audio/x-aifc" -> AudioCodec.AIFF
        "audio/mpeg", "audio/mp3", "audio/mpeg3", "audio/x-mpeg-3" -> AudioCodec.MP3
        "audio/mp4a-latm", "audio/aac", "audio/aac-adts", "audio/x-aac" -> AudioCodec.AAC
        "audio/ogg", "application/ogg", "audio/opus", "audio/vorbis", "audio/x-vorbis" ->
            AudioCodec.OGG
        else -> AudioCodec.UNKNOWN
    }

    /** Resuelve el códec a partir de una extensión (con o sin punto inicial). */
    fun fromExtension(extension: String?): AudioCodec =
        when (extension?.trim()?.lowercase()?.removePrefix(".")) {
            "flac" -> AudioCodec.FLAC
            "wav", "wave" -> AudioCodec.WAV
            "aiff", "aif", "aifc" -> AudioCodec.AIFF
            "mp3" -> AudioCodec.MP3
            "aac" -> AudioCodec.AAC
            "ogg", "oga", "opus" -> AudioCodec.OGG
            // "m4a"/"mp4" son ambiguos (AAC o ALAC): no se fuerza identificación.
            else -> AudioCodec.UNKNOWN
        }

    /**
     * Estrategia: MIME primero; si no identifica el códec, extensión como respaldo.
     * Devuelve [AudioCodec.UNKNOWN] cuando ninguno de los dos permite confiar.
     */
    fun resolve(mime: String?, extension: String?): AudioCodec {
        val byMime = fromMime(mime)
        return if (byMime != AudioCodec.UNKNOWN) byMime else fromExtension(extension)
    }
}
