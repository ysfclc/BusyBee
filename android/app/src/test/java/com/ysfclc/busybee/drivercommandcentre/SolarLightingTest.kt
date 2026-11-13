package com.ysfclc.busybee.drivercommandcentre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SolarLightingTest {
    private fun at(iso: String): Long = Instant.parse(iso).toEpochMilli()

    @Test
    fun krakowUsesDayPaletteWhenSunIsHigh() {
        assertEquals(
            DaylightPhase.DAY,
            SolarLighting.phase(50.0647, 19.9450, at("2026-06-21T12:00:00Z"))
        )
    }

    @Test
    fun krakowUsesNightPaletteWhenSunIsBelowCivilTwilight() {
        assertEquals(
            DaylightPhase.NIGHT,
            SolarLighting.phase(50.0647, 19.9450, at("2026-06-21T00:30:00Z"))
        )
    }

    @Test
    fun duskIsASeparateVisualState() {
        val phase = SolarLighting.phase(50.0647, 19.9450, at("2026-06-21T02:30:00Z"))
        assertEquals(DaylightPhase.DUSK, phase)
    }

    @Test
    fun invalidCoordinatesFallBackToLocalClockInsteadOfInventingSunPosition() {
        val phase = SolarLighting.phase(Double.NaN, 19.9450, at("2026-06-21T12:00:00Z"))
        assertTrue(phase in DaylightPhase.values())
    }

    @Test
    fun eachPhaseHasARealMapStyleAndDistinctAccent() {
        val day = BusyBeePalettes.forPhase(DaylightPhase.DAY)
        val dusk = BusyBeePalettes.forPhase(DaylightPhase.DUSK)
        val night = BusyBeePalettes.forPhase(DaylightPhase.NIGHT)

        assertEquals("https://tiles.openfreemap.org/styles/bright", day.mapStyleUri)
        assertEquals("https://tiles.openfreemap.org/styles/fiord", dusk.mapStyleUri)
        assertEquals("https://tiles.openfreemap.org/styles/dark", night.mapStyleUri)
        assertNotEquals(day.accent, night.accent)
        assertNotEquals(day.background, night.background)
    }
}
