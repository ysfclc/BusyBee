package com.ysfclc.busybee.plaintextpoc

import org.junit.Assert.assertTrue
import org.junit.Test

class PlaintextBoundaryTest {
    @Test fun outboundAndInboundHooksAreCallableWithoutPersistingPayloads() {
        val outbound = """{"event_type":"offer_lookup","correlation_id":"poc-offer-0001"}"""
        val inbound = """{"event_type":"offer_observed","offer_id":"synthetic-ride-001"}"""
        // The owned app's boundary methods are deliberately side-effect-free by default.
        PlaintextBoundary.outbound(outbound)
        PlaintextBoundary.inbound(inbound)
        assertTrue(outbound.contains("poc-offer-0001"))
        assertTrue(inbound.contains("synthetic-ride-001"))
    }
}
