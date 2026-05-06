package com.ssafy.smartcane.navigation

import com.ssafy.smartcane.network.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteNavigationMatcherTest {
    private val route = listOf(
        RoutePoint(longitude = 126.8100, latitude = 35.2000),
        RoutePoint(longitude = 126.8110, latitude = 35.2000),
        RoutePoint(longitude = 126.8120, latitude = 35.2000)
    )

    @Test
    fun match_snapsNearbyPointToRoute() {
        val result = RouteNavigationMatcher.match(
            location = RoutePoint(longitude = 126.8105, latitude = 35.20003),
            routePoints = route
        )

        assertNotNull(result)
        requireNotNull(result)
        assertEquals(RouteDeviationStatus.ON_ROUTE, result.status)
        assertEquals(35.2000, result.snappedPoint.latitude, 0.00001)
        assertTrue(result.traveledDistanceMeters > 40f)
        assertTrue(result.remainingDistanceMeters > 40f)
    }

    @Test
    fun match_marksFarPointAsOffRoute() {
        val result = RouteNavigationMatcher.match(
            location = RoutePoint(longitude = 126.8105, latitude = 35.2005),
            routePoints = route
        )

        assertNotNull(result)
        requireNotNull(result)
        assertEquals(RouteDeviationStatus.OFF_ROUTE, result.status)
        assertTrue(result.distanceToRouteMeters > 30f)
    }

    @Test
    fun match_returnsNullForInvalidPolyline() {
        val result = RouteNavigationMatcher.match(
            location = RoutePoint(longitude = 126.8105, latitude = 35.2000),
            routePoints = listOf(RoutePoint(longitude = 126.8100, latitude = 35.2000))
        )

        assertEquals(null, result)
    }
}
