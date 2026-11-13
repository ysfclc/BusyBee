package com.ysfclc.busybee.drivercommandcentre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.geometry.LatLng
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoutePlannerTest {
    @Test fun destinationSearchParsesMultipleValidPolishCandidatesAndSkipsBadRows() {
        val json = """[
          {"display_name":"Rynek Główny, Kraków, Polska","lat":"50.0614","lon":"19.9366"},
          {"display_name":"Plac Nowy, Kraków, Polska","lat":"50.0515","lon":"19.9440"},
          {"display_name":"Bad row","lat":"x","lon":"y"}
        ]"""
        val results = RoutePlanner.parseDestinationCandidates(json)
        assertEquals(2, results.size)
        assertEquals("Rynek Główny, Kraków, Polska", results[0].displayName)
        assertEquals(50.0614, results[0].location.latitude, 0.00001)
    }

    @Test fun destinationSearchUsesPolishNamesAndStaysInsideTheDriverArea() {
        val url = RoutePlanner.destinationSearchUrl("ulica Długa 8", LatLng(50.061, 19.937))
        assertTrue(url.contains("accept-language=pl"))
        assertTrue(url.contains("countrycodes=pl"))
        assertTrue(url.contains("viewbox="))
        assertTrue(!url.contains("bounded=1"))
        assertTrue(url.contains("viewbox="))
    }

    @Test fun routeInstructionsFallBackToRoadReferenceWhenNameIsMissing() {
        val json = """{
          "code":"Ok",
          "routes":[{
            "distance":1000,"duration":180,
            "geometry":{"coordinates":[[19.93,50.06],[19.935,50.061],[19.94,50.062]]},
            "legs":[{"steps":[
              {"distance":400,"duration":70,"name":"","ref":"DK7","maneuver":{"type":"depart","location":[19.93,50.06]}},
              {"distance":600,"duration":110,"name":"","ref":"DW780","maneuver":{"type":"turn","modifier":"left","location":[19.935,50.061]}}
            ]}]
          }]
        }"""
        val route = RoutePlanner.parseRouteResponse(json, LatLng(50.062, 19.94), "Test").first()
        assertEquals("DW780", route.steps[1].roadName)
        assertTrue(route.steps[1].instruction.contains("DW780"))
    }

    @Test fun routeResponseRetainsManeuversAndCalculatesProgress() {
        val json = """{
          "code":"Ok",
          "routes":[{
            "distance":1000,"duration":180,
            "geometry":{"coordinates":[[19.93,50.06],[19.935,50.061],[19.94,50.062]]},
            "legs":[{"steps":[
              {"distance":400,"duration":70,"name":"Start Street","maneuver":{"type":"depart","location":[19.93,50.06]}},
              {"distance":600,"duration":110,"name":"Main Street","maneuver":{"type":"turn","modifier":"left","location":[19.935,50.061]}},
              {"distance":0,"duration":0,"name":"","maneuver":{"type":"arrive","location":[19.94,50.062]}}
            ]}]
          }]
        }"""
        val destination = LatLng(50.062, 19.94)
        val route = RoutePlanner.parseRouteResponse(json, destination, "Test destination").first()
        assertEquals(3, route.steps.size)
        assertTrue(route.steps[1].instruction.contains("Turn left onto Main Street"))
        assertTrue(route.steps[1].distanceAlongRouteMeters > 0.0)
        assertEquals(1.0, route.distanceKm, 0.001)
        assertEquals(3.0, route.durationMinutes, 0.001)
        assertTrue(RoutePlanner.progressMeters(LatLng(50.061, 19.935), route.points) > 0.0)
        assertTrue(RoutePlanner.distanceToRouteMeters(LatLng(50.061, 19.935), route.points) < 1.0)
    }

    @Test fun gpsProgressProjectsOntoTheRouteSegmentBetweenVertices() {
        val points = listOf(LatLng(50.0, 19.90), LatLng(50.0, 19.91))
        val midpoint = LatLng(50.0 + 20.0 / 111_195.0, 19.905)

        val total = RoutePlanner.distanceMeters(points[0], points[1])
        val distance = RoutePlanner.distanceToRouteMeters(midpoint, points)
        val progress = RoutePlanner.progressMeters(midpoint, points)

        assertEquals(20.0, distance, 1.0)
        assertEquals(total / 2.0, progress, 1.0)
    }

    @Test fun alternativeRankingKeepsFastestDistinctRoutes() {
        val destination = LatLng(50.1, 19.9)
        val points = listOf(LatLng(50.0, 19.8), destination)
        val routes = listOf(
            RoutePlan("A", destination, points, 7.0, 12.0),
            RoutePlan("B", destination, points, 8.0, 9.0),
            RoutePlan("B duplicate", destination, points, 8.0, 9.0)
        )
        val ranked = RouteOptionRanker.rank(routes)
        assertEquals(listOf(9.0, 12.0), ranked.map { it.durationMinutes })
    }
}
