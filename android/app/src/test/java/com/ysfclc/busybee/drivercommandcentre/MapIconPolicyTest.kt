package com.ysfclc.busybee.drivercommandcentre

import org.junit.Assert.assertEquals
import org.junit.Test

class MapIconPolicyTest {
    @Test
    fun mapsKnownRoadEventCategoriesToDistinctSemantics() {
        assertEquals(MapEventMarkerKind.ROADWORKS, MapIconPolicy.eventKind("Roadworks"))
        assertEquals(MapEventMarkerKind.ROADWORKS, MapIconPolicy.eventKind("construction"))
        assertEquals(MapEventMarkerKind.HAZARD, MapIconPolicy.eventKind("Hazards"))
        assertEquals(MapEventMarkerKind.HAZARD, MapIconPolicy.eventKind("accident"))
        assertEquals(MapEventMarkerKind.CLOSURE, MapIconPolicy.eventKind("Road closure"))
        assertEquals(MapEventMarkerKind.WEATHER, MapIconPolicy.eventKind("weather"))
        assertEquals(MapEventMarkerKind.OTHER, MapIconPolicy.eventKind("community note"))
    }
}
