package com.alexis.hifiplayer.domain.model

/**
 * Represents audio codec type for source media (independent from Android APIs).
 *
 * [isLossless] classifies the format when it can actually be determined:
 * - `true`: sin pérdida (FLAC, WAV/PCM, ALAC, AIFF).
 * - `false`: con pérdida (MP3, AAC).
 * - `null`: no determinable o ambiguo ([OGG], [UNKNOWN]).
 *
 * Nota sobre [OGG]: es un **contenedor** y puede transportar Vorbis u Opus (con
 * pérdida) o FLAC (sin pérdida). Como este enum no distingue el códec interno, su
 * `isLossless` es `null` (desconocido). Nunca se asume lossless para [UNKNOWN] ni se
 * marca [OGG] como lossy solo por ser OGG.
 */
enum class AudioCodec(val isLossless: Boolean?) {
    FLAC(true),
    WAV(true),
    ALAC(true),
    AIFF(true),
    MP3(false),
    AAC(false),
    OGG(null),
    UNKNOWN(null)
}
