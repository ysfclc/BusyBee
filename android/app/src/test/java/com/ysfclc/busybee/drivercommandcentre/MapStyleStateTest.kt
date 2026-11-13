package com.ysfclc.busybee.drivercommandcentre

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapStyleStateTest {
    @Test
    fun mapIsNotReadyUntilTheStyleLoadedCallbackRuns() {
        val state = MapStyleState()

        assertFalse(state.isReady)

        state.markReady()

        assertTrue(state.isReady)
    }

    @Test
    fun repeatedStyleLoadedCallbacksAreIdempotent() {
        val state = MapStyleState()

        state.markReady()
        state.markReady()

        assertTrue(state.isReady)
    }
}
