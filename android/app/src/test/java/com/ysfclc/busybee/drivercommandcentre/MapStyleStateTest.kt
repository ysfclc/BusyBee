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
    fun activationIsDeferredWhileActivityIsPaused() {
        val state = MapStyleState()
        state.markReady()

        assertFalse(state.claimActivation(isResumed = false, hasMap = true))
        assertTrue(state.claimActivation(isResumed = true, hasMap = true))
    }

    @Test
    fun activationWaitsForMapInstance() {
        val state = MapStyleState()
        state.markReady()

        assertFalse(state.claimActivation(isResumed = true, hasMap = false))
        assertTrue(state.claimActivation(isResumed = true, hasMap = true))
    }

    @Test
    fun activationCanOnlyBeClaimedOnce() {
        val state = MapStyleState()
        state.markReady()

        assertTrue(state.claimActivation(isResumed = true, hasMap = true))
        assertFalse(state.claimActivation(isResumed = true, hasMap = true))
    }

    @Test
    fun repeatedStyleLoadedCallbacksDoNotResetActivation() {
        val state = MapStyleState()
        state.markReady()
        assertTrue(state.claimActivation(isResumed = true, hasMap = true))

        state.markReady()

        assertFalse(state.claimActivation(isResumed = true, hasMap = true))
    }
}
