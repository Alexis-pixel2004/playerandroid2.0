package com.alexis.hifiplayer.domain.model

/**
 * Represents honest bit-perfect/diagnostic status.
 * - BIT_PERFECT: sufficient evidence path preserves format, no processing invalidating it
 * - LOSSLESS: content remains lossless, but bit-perfect cannot be guaranteed
 * - CONVERTED: evidence of conversion (sample-rate, bit-depth, encoding, downmix, etc.)
 * - UNAVAILABLE: insufficient information or no valid output
 */
enum class BitPerfectStatus {
    BIT_PERFECT,
    LOSSLESS,
    CONVERTED,
    UNAVAILABLE
}
