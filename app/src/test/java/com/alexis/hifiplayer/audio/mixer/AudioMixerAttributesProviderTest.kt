package com.alexis.hifiplayer.audio.mixer

import org.junit.Assert.*
import org.junit.Test

class AudioMixerAttributesProviderTest {
    @Test
    fun constantsExist() {
        assertEquals("MIXER", MixerBehavior.MIXER.name)
        assertEquals("BIT_PERFECT", MixerBehavior.BIT_PERFECT.name)
        assertEquals("UNKNOWN", MixerBehavior.UNKNOWN.name)
    }
}
