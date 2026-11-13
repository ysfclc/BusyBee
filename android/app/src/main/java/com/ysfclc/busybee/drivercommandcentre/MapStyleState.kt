package com.ysfclc.busybee.drivercommandcentre

/**
 * Tracks MapLibre style readiness and grants one activation only when the owning
 * Activity is resumed and the MapLibre instance is available.
 */
internal class MapStyleState {
    var isReady: Boolean = false
        private set

    private var activationClaimed = false

    fun markReady() {
        isReady = true
    }

    /** Returns true once, and only once, when both lifecycle and map prerequisites hold. */
    fun claimActivation(isResumed: Boolean, hasMap: Boolean): Boolean {
        if (!isReady || !isResumed || !hasMap || activationClaimed) return false
        activationClaimed = true
        return true
    }
}
