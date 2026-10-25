package com.alexis.hifiplayer.audio

import com.alexis.hifiplayer.domain.model.AudioFormat
import com.alexis.hifiplayer.domain.model.AudioOutputFormat
import com.alexis.hifiplayer.domain.model.AudioSourceFormat
import com.alexis.hifiplayer.domain.model.PcmEncoding
import org.junit.Assert.*
import org.junit.Test

class AudioEngineStateTest {
    @Test
    fun initialState_idle() {
        val status = AudioEngineStatus(state = AudioEngineState.IDLE)
        assertEquals(AudioEngineState.IDLE, status.state)
        assertNull(status.error)
    }

    @Test
    fun lifecycle_transitions() {
        assertNotSame(AudioEngineState.IDLE, AudioEngineState.PREPARED)
        assertNotSame(AudioEngineState.PLAYING, AudioEngineState.PAUSED)
        assertNotSame(AudioEngineState.STOPPED, AudioEngineState.RELEASED)
    }
}
