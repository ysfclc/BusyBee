package com.ysfclc.busybee.drivercommandcentre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.geometry.LatLng
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MapSpatialDataTest {
    @Test
    fun parsesNamedBuildingsAndAddressedBuildings() {
        val result = MapPlaceLookup.parse(
            """{"elements":[
                {"type":"way","id":101,"center":{"lat":50.061,"lon":19.937},"tags":{"building":"apartments","name:pl":"Kamienica Pod Aniołem","name":"House of Angels","addr:street":"Świętego Jana","addr:housenumber":"12","building:levels":"4"}},
                {"type":"way","id":102,"center":{"lat":50.062,"lon":19.938},"tags":{"building":"residential","addr:street":"Długa","addr:housenumber":"8"}}
            ]}"""
        )
        assertEquals(2, result.size)
        assertEquals("Kamienica Pod Aniołem", result[0].label)
        assertTrue(result[0].detail.contains("Świętego Jana 12"))
        assertEquals("Długa 8", result[1].label)
    }

    @Test
    fun entranceLabelsUseMappedTagsAndDoNotInventStairwellLetters() {
        val result = MapPlaceLookup.parse(
            """{"elements":[
                {"type":"node","id":201,"lat":50.061,"lon":19.937,"tags":{"entrance":"staircase","ref":"A","level":"0"}},
                {"type":"node","id":202,"lat":50.062,"lon":19.938,"tags":{"entrance":"staircase","level":"1"}},
                {"type":"node","id":203,"lat":50.063,"lon":19.939,"tags":{"entrance":"yes","ref":"B"}},
                {"type":"node","id":204,"lat":50.064,"lon":19.940,"tags":{"entrance":"main","name:pl":"Wejście od ulicy","name":"Front entrance"}}
            ]}"""
        )
        assertEquals(listOf("Klatka A", "Klatka schodowa", "Wejście · B", "Wejście od ulicy"), result.map { it.label })
        assertTrue(result[0].detail.contains("Reference A"))
        assertTrue(result[0].detail.contains("Level 0"))
    }

    @Test
    fun ignoresUnnamedUnaddressedBuildingsAndUnmappedNodes() {
        val result = MapPlaceLookup.parse(
            """{"elements":[
                {"type":"way","id":301,"center":{"lat":50.061,"lon":19.937},"tags":{"building":"yes"}},
                {"type":"node","id":302,"lat":50.062,"lon":19.938,"tags":{"amenity":"bench"}}
            ]}"""
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun cameraTargetMovesForwardAlongBearing() {
        val origin = LatLng(50.0, 19.0)
        val north = NavigationCameraMath.aheadOf(origin, 0.0, 100.0)
        val east = NavigationCameraMath.aheadOf(origin, 90.0, 100.0)
        assertTrue(north.latitude > origin.latitude)
        assertEquals(origin.longitude, north.longitude, 0.00001)
        assertTrue(east.longitude > origin.longitude)
        assertEquals(origin.latitude, east.latitude, 0.00001)
    }

    @Test
    fun overpassQueryRequestsBuildingAddressesAndEntrances() {
        val query = MapPlaceLookup.query(50.061, 19.937)
        assertTrue(query.contains("[\\"building\\"][\\"name\\"]"))
        assertTrue(query.contains("[\\"addr:housenumber\\"]"))
        assertTrue(query.contains("[\\"entrance\\"]"))
        assertTrue(query.contains("out center tags"))
    }
}
