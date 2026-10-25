package com.alexis.hifiplayer.domain.model

/**
 * Represents three-state for processing flags (avoids conflating UNKNOWN with OFF).
 */
enum class ProcessingState {
    ON,
    OFF,
    UNKNOWN
}
