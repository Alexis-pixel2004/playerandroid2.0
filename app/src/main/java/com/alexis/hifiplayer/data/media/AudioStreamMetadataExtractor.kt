package com.alexis.hifiplayer.data.media

import android.content.Context
import android.media.AudioFormat as AndroidAudioFormat
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.alexis.hifiplayer.domain.model.AudioCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Origen (procedencia) de un dato técnico extraído del archivo.
 *
 * Permite separar lo **confirmado** ([CONTAINER], [MEDIA_METADATA_RETRIEVER]) de lo
 * **inferido** ([FILE_EXTENSION]) y de lo **desconocido** ([NONE]).
 */
enum class MetadataSource {
    /** Confirmado a partir de la pista/contenedor vía [MediaExtractor]. */
    CONTAINER,

    /** Confirmado a partir del contenido vía [MediaMetadataRetriever] (alternativa). */
    MEDIA_METADATA_RETRIEVER,

    /** Inferido únicamente a partir de la extensión del archivo; no confirmado. */
    FILE_EXTENSION,

    /** No disponible. */
    NONE
}

/**
 * Especificación técnica del stream de audio **original** de un archivo.
 *
 * Usa `null` para representar "desconocido": nunca se usa `0` como sustituto de un
 * dato ausente. Los campos numéricos (`sampleRateHz`, `channelCount`, `bitDepth`,
 * `bitRate`) son `null` cuando la API pública no los expone, y cada uno lleva su
 * [MetadataSource] asociado para distinguir lo confirmado de lo inferido.
 *
 * NOTA: esto describe el **archivo original**, no el formato decodificado ni el
 * solicitado a la salida. No implica ni permite afirmar bit-perfect.
 */
data class AudioStreamMetadata(
    val codec: AudioCodec,
    val codecSource: MetadataSource,
    val sampleRateHz: Int?,
    val sampleRateSource: MetadataSource,
    val channelCount: Int?,
    val channelCountSource: MetadataSource,
    val bitDepth: Int?,
    val bitDepthSource: MetadataSource,
    val bitRate: Long?,
    val bitRateSource: MetadataSource,
    /** MIME del contenedor tal como lo reporta [MediaExtractor], o `null`. */
    val mimeType: String?,
    /** Incertidumbres y avisos acumulados durante la extracción. */
    val uncertainties: List<String>
) {
    /**
     * `true`/`false` si el códec está confirmado como lossless/lossy, `null` si no
     * se puede determinar. Derivado de [codec]; no se almacena.
     */
    val isLossless: Boolean? get() = codec.isLossless
}

/** Resultado tipado de una extracción de metadatos. */
sealed interface AudioStreamMetadataResult {

    /** Se pudo leer una pista de audio; [metadata] contiene lo que se pudo determinar. */
    data class Available(val metadata: AudioStreamMetadata) : AudioStreamMetadataResult

    /**
     * El archivo es legible pero no contiene una pista de audio reconocible
     * (no es un fallo de lectura).
     */
    data class Unsupported(
        val reason: String,
        val mimeType: String? = null
    ) : AudioStreamMetadataResult

    /**
     * No se pudo leer el archivo (URI inaccesible, permiso denegado/revocado,
     * archivo dañado, proveedor que lanza excepción, etc.).
     */
    data class Failed(
        val reason: String,
        val cause: Throwable? = null
    ) : AudioStreamMetadataResult
}

/**
 * Extrae los metadatos técnicos del stream de audio original de un archivo.
 *
 * Es Android-específico y vive en `data/`; no reproduce audio, no toca la cola, no
 * crea [android.media.AudioTrack] ni interviene en el motor (ExoPlayer sigue siendo
 * el único motor de reproducción).
 *
 * [extract] es una función `suspend` que se ejecuta internamente en
 * [Dispatchers.IO], de modo que el llamador nunca bloquea el hilo principal.
 *
 * Limitaciones conocidas de las API públicas de Android (documentadas aquí para no
 * sobreestimar los datos):
 * - [MediaExtractor] suele exponer la frecuencia de muestreo y el número de canales,
 *   pero **no** la profundidad de bits de formatos comprimidos (FLAC/ALAC).
 * - [MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE] y
 *   [MediaMetadataRetriever.METADATA_KEY_SAMPLERATE] (API 31+) documentan su valor
 *   como "si está disponible"; con frecuencia devuelven `null` según dispositivo y
 *   formato. Cuando no están disponibles, el campo queda `null` (desconocido).
 * - El bit depth obtenido de `KEY_PCM_ENCODING` solo existe para PCM crudo.
 *
 * Por todo ello, un archivo lossless **no** garantiza bit-perfect: eso requiere
 * pruebas con DAC/hardware externo y queda fuera del alcance de este extractor.
 */
class AudioStreamMetadataExtractor(private val context: Context) {

    /**
     * @param contentUri URI de contenido (`content://…`) del archivo.
     * @param mimeTypeHint pista MIME declarada por MediaStore (opcional, no autoritativa).
     * @param fileExtension extensión sin punto declarada por MediaStore (opcional).
     */
    suspend fun extract(
        contentUri: String,
        mimeTypeHint: String = "",
        fileExtension: String = ""
    ): AudioStreamMetadataResult = withContext(Dispatchers.IO) {
        val uri = parseUriOrNull(contentUri)
            ?: return@withContext AudioStreamMetadataResult.Failed(
                reason = "URI de contenido vacía o inválida"
            )

        var extractor: MediaExtractor? = null
        var retriever: MediaMetadataRetriever? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)

            val track = findAudioTrack(extractor)
                ?: return@withContext AudioStreamMetadataResult.Unsupported(
                    reason = "El archivo no contiene una pista de audio reconocible"
                )

            // --- Datos confirmados por el contenedor (MediaExtractor) ---
            val containerMime = track.getString(MediaFormat.KEY_MIME)
            val containerSampleRate = track.intOrNull(MediaFormat.KEY_SAMPLE_RATE)
            val containerChannels = track.intOrNull(MediaFormat.KEY_CHANNEL_COUNT)
            val containerBitRate = track.longOrNull(MediaFormat.KEY_BIT_RATE)
            val containerBitDepth =
                pcmEncodingToBitDepth(track.intOrNull(MediaFormat.KEY_PCM_ENCODING))

            // Códec: el MIME del contenedor es autoritativo; la extensión solo infiere.
            val codecFromMime = AudioCodecMapper.fromMime(containerMime)
            val codecFromExtension = AudioCodecMapper.fromExtension(fileExtension)

            val codec: AudioCodec
            val codecSource: MetadataSource
            when {
                codecFromMime != AudioCodec.UNKNOWN -> {
                    codec = codecFromMime
                    codecSource = MetadataSource.CONTAINER
                }
                codecFromExtension != AudioCodec.UNKNOWN -> {
                    codec = codecFromExtension
                    codecSource = MetadataSource.FILE_EXTENSION
                }
                else -> {
                    codec = AudioCodec.UNKNOWN
                    codecSource = MetadataSource.NONE
                }
            }

            // --- Fallback vía MediaMetadataRetriever para los campos que falten ---
            var retrieverSampleRate: Int? = null
            var retrieverBitDepth: Int? = null
            if (containerSampleRate == null || containerBitDepth == null) {
                retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                if (containerSampleRate == null) {
                    retrieverSampleRate = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                        ?.toIntOrNull()
                }
                if (containerBitDepth == null) {
                    retrieverBitDepth = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)
                        ?.toIntOrNull()
                }
            }

            val sampleRateHz = containerSampleRate ?: retrieverSampleRate
            val sampleRateSource = when {
                containerSampleRate != null -> MetadataSource.CONTAINER
                retrieverSampleRate != null -> MetadataSource.MEDIA_METADATA_RETRIEVER
                else -> MetadataSource.NONE
            }

            val bitDepth = containerBitDepth ?: retrieverBitDepth
            val bitDepthSource = when {
                containerBitDepth != null -> MetadataSource.CONTAINER
                retrieverBitDepth != null -> MetadataSource.MEDIA_METADATA_RETRIEVER
                else -> MetadataSource.NONE
            }

            // Si MediaStore declara un MIME que contradice al del contenedor, se deja
            // constancia; el contenedor prevalece.
            val codecFromHint = AudioCodecMapper.fromMime(mimeTypeHint)
            val hintDisagrees = codecSource == MetadataSource.CONTAINER &&
                codecFromHint != AudioCodec.UNKNOWN &&
                codecFromHint != codec

            val uncertainties = buildList {
                if (codecSource == MetadataSource.FILE_EXTENSION) {
                    add("Códec inferido solo por la extensión; no confirmado por el contenedor")
                }
                if (codec == AudioCodec.UNKNOWN) {
                    add("Códec no identificado")
                }
                if (hintDisagrees) {
                    add(
                        "El MIME declarado por MediaStore ($mimeTypeHint) no coincide con " +
                            "el del contenedor ($containerMime)"
                    )
                }
                if (containerMime.equals("audio/raw", ignoreCase = true)) {
                    add("MIME 'audio/raw' asumido como contenedor WAV/PCM")
                }
                if (sampleRateHz == null) add("Frecuencia de muestreo no disponible")
                if (containerChannels == null) add("Número de canales no disponible")
                if (bitDepth == null) add("Profundidad de bits no disponible vía API pública")
                if (containerBitRate == null) add("Bitrate no disponible")
            }

            AudioStreamMetadataResult.Available(
                AudioStreamMetadata(
                    codec = codec,
                    codecSource = codecSource,
                    sampleRateHz = sampleRateHz,
                    sampleRateSource = sampleRateSource,
                    channelCount = containerChannels,
                    channelCountSource = if (containerChannels == null) {
                        MetadataSource.NONE
                    } else {
                        MetadataSource.CONTAINER
                    },
                    bitDepth = bitDepth,
                    bitDepthSource = bitDepthSource,
                    bitRate = containerBitRate,
                    bitRateSource = if (containerBitRate == null) {
                        MetadataSource.NONE
                    } else {
                        MetadataSource.CONTAINER
                    },
                    mimeType = containerMime,
                    uncertainties = uncertainties
                )
            )
        } catch (e: FileNotFoundException) {
            AudioStreamMetadataResult.Failed("Archivo no encontrado o inaccesible", e)
        } catch (e: SecurityException) {
            AudioStreamMetadataResult.Failed("Permiso denegado o revocado para el archivo", e)
        } catch (e: IOException) {
            AudioStreamMetadataResult.Failed("Error de lectura o archivo dañado", e)
        } catch (e: IllegalArgumentException) {
            AudioStreamMetadataResult.Failed("URI o formato no soportado por el proveedor", e)
        } catch (e: RuntimeException) {
            AudioStreamMetadataResult.Failed("Fallo inesperado al leer el archivo", e)
        } finally {
            releaseQuietly(extractor)
            releaseQuietly(retriever)
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): MediaFormat? {
        for (i in 0 until extractor.trackCount) {
            val format = runCatching { extractor.getTrackFormat(i) }.getOrNull() ?: continue
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime != null && mime.startsWith("audio/", ignoreCase = true)) {
                return format
            }
        }
        return null
    }

    private fun releaseQuietly(extractor: MediaExtractor?) {
        runCatching { extractor?.release() }
    }

    private fun releaseQuietly(retriever: MediaMetadataRetriever?) {
        runCatching { retriever?.release() }
    }

    private fun parseUriOrNull(raw: String): Uri? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val uri = Uri.parse(trimmed)
        return uri.takeIf { it.toString().isNotEmpty() }
    }
}

private fun MediaFormat.intOrNull(key: String): Int? =
    if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null

private fun MediaFormat.longOrNull(key: String): Long? {
    if (!containsKey(key)) return null
    runCatching { getInteger(key) }.getOrNull()?.let { return it.toLong() }
    return runCatching { getLong(key) }.getOrNull()
}

/**
 * Traduce un `AudioFormat.ENCODING_PCM_*` de Android a profundidad de bits en
 * términos de dominio. Devuelve `null` cuando no aplica (p. ej. ningún encoding
 * declarado, como ocurre en formatos comprimidos).
 */
private fun pcmEncodingToBitDepth(encoding: Int?): Int? = when (encoding) {
    AndroidAudioFormat.ENCODING_PCM_8BIT -> 8
    AndroidAudioFormat.ENCODING_PCM_16BIT -> 16
    AndroidAudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
    AndroidAudioFormat.ENCODING_PCM_32BIT -> 32
    AndroidAudioFormat.ENCODING_PCM_FLOAT -> 32
    else -> null
}
