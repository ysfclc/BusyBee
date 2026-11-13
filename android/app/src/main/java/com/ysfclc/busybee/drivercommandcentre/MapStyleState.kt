package com.ysfclc.busybee.drivercommandcentre

/**
 * Tracks whether MapLibre has completed its asynchronous style load.
 * Map annotation APIs must not be called before this transition.
 */
internal class MapStyleState {
    var isReady: Boolean = false
        private set

    fun markReady() {
        isReady = true
    }
}
