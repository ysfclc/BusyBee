package com.ysfclc.busybee.drivercommandcentre

import org.junit.Assert.assertEquals
import org.junit.Test
import org.maplibre.android.geometry.LatLng

class RouteOptionRankerTest {
    private fun route(distance:Double,duration:Double)=RoutePlan(
        destinationName="Krakow destination",
        destination=LatLng(50.0,19.0),
        points=listOf(LatLng(50.0,19.0),LatLng(50.1,19.1)),
        distanceKm=distance,
        durationMinutes=duration
    )

    @Test fun fastestRouteRanksFirstAndDistanceBreaksTies() {
        val ranked=RouteOptionRanker.rank(listOf(route(8.0,20.0),route(7.0,20.0),route(10.0,18.0)))
        assertEquals(18.0,ranked.first().durationMinutes,0.001)
        assertEquals(7.0,ranked[1].distanceKm,0.001)
    }

    @Test fun removesDuplicateMetricsAndCapsToThreeCandidates() {
        val ranked=RouteOptionRanker.rank(listOf(
            route(8.0,20.0),route(8.0,20.0),route(9.0,21.0),route(10.0,22.0),route(11.0,23.0)
        ))
        assertEquals(3,ranked.size)
    }
}
