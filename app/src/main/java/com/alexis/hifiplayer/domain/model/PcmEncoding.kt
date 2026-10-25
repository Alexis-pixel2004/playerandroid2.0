package com.alexis.hifiplayer.domain.model

/**
 * Represents PCM encoding format in domain terms (agnostic from Android APIs).
 */
enum class PcmEncoding {
    PCM_16,
    PCM_24,
    PCM_32,
    FLOAT,
    UNKNOWN
}
