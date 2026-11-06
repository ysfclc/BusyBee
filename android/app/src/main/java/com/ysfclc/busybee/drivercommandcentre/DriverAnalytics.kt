package com.ysfclc.busybee.drivercommandcentre

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PlatformEconomics(
    val platform: String,
    val tripCount: Int,
    val grossPln: Double?,
    val feesPln: Double?,
    val bonusesPln: Double?,
    val knownNetBeforeVehiclePln: Double?,
    val estimatedVehicleCostPln: Double?,
    val estimatedNetAfterVehiclePln: Double?,
    val completeNetTrips: Int,
    val tripsWithDistance: Int,
    val paidDistanceKm: Double?
)

data class HourEconomics(
    val hour: Int,
    val trips: Int,
    val grossPln: Double?,
    val completeNetPln: Double?,
    val recordsWithTime: Int
)

data class DriverAnalytics(
    val tripCount: Int,
    val plnTripCount: Int,
    val tripsWithGross: Int,
    val tripsWithFees: Int,
    val tripsWithBonus: Int,
    val tripsWithDistance: Int,
    val tripsWithTime: Int,
    val estimatedNetTrips: Int,
    val estimatedNetPln: Double?,
    val platforms: List<PlatformEconomics>,
    val hours: List<HourEconomics>,
    val dataWarnings: List<String>
)

object DriverAnalyticsEngine {
    private val tripTypes = setOf("trip", "ride", "completed_trip")

    fun calculate(records: List<DriverRecord>, vehicleCostPerKm: Double): DriverAnalytics {
        require(vehicleCostPerKm.isFinite() && vehicleCostPerKm >= 0.0) { "Vehicle cost must be a non-negative number" }
        val trips = records.filter { it.type.trim().lowercase(Locale.ROOT) in tripTypes }
        val plnTrips = trips.filter { it.currency.equals("PLN", ignoreCase = true) }
        val completeNet = plnTrips.mapNotNull { record ->
            val gross = record.gross
            val fees = record.fees
            val bonus = record.bonus
            if (gross == null || fees == null || bonus == null) null else {
                val vehicle = record.distanceKm?.takeIf { it >= 0.0 }?.times(vehicleCostPerKm)
                if (vehicle == null) null else gross - fees + bonus - vehicle
            }
        }
        val platformStats = plnTrips.groupBy { it.platform.ifBlank { "Unknown" } }
            .map { (platform, rows) ->
                val complete = rows.filter { it.gross != null && it.fees != null && it.bonus != null }
                val withDistance = rows.filter { it.distanceKm != null && it.distanceKm >= 0.0 }
                val netCompleteWithDistance = complete.filter { it.distanceKm != null && it.distanceKm >= 0.0 }
                PlatformEconomics(
                    platform = platform,
                    tripCount = rows.size,
                    grossPln = rows.mapNotNull { it.gross }.takeIf { it.isNotEmpty() }?.sum(),
                    feesPln = rows.mapNotNull { it.fees }.takeIf { it.isNotEmpty() }?.sum(),
                    bonusesPln = rows.mapNotNull { it.bonus }.takeIf { it.isNotEmpty() }?.sum(),
                    knownNetBeforeVehiclePln = complete.map { it.gross!! - it.fees!! + it.bonus!! }.takeIf { it.isNotEmpty() }?.sum(),
                    estimatedVehicleCostPln = withDistance.mapNotNull { it.distanceKm }.sum().takeIf { withDistance.isNotEmpty() }?.times(vehicleCostPerKm),
                    estimatedNetAfterVehiclePln = netCompleteWithDistance.map { it.gross!! - it.fees!! + it.bonus!! - it.distanceKm!! * vehicleCostPerKm }
                        .takeIf { it.isNotEmpty() }?.sum(),
                    completeNetTrips = complete.size,
                    tripsWithDistance = withDistance.size,
                    paidDistanceKm = withDistance.mapNotNull { it.distanceKm }.takeIf { it.isNotEmpty() }?.sum()
                )
            }.sortedByDescending { it.estimatedNetAfterVehiclePln ?: Double.NEGATIVE_INFINITY }

        val byHour = plnTrips.mapNotNull { record ->
            val hour = parseHour(record.occurredAt) ?: return@mapNotNull null
            hour to record
        }.groupBy({ it.first }, { it.second }).map { (hour, rows) ->
            val complete = rows.filter { it.gross != null && it.fees != null && it.bonus != null }
            HourEconomics(
                hour,
                rows.size,
                rows.mapNotNull { it.gross }.takeIf { it.isNotEmpty() }?.sum(),
                complete.map { it.gross!! - it.fees!! + it.bonus!! }.takeIf { it.isNotEmpty() }?.sum(),
                rows.size
            )
        }.sortedByDescending { it.completeNetPln ?: Double.NEGATIVE_INFINITY }

        val warnings = buildList {
            if (plnTrips.isEmpty() && trips.isNotEmpty()) add("No PLN trip records; currencies are kept separate.")
            if (plnTrips.any { it.gross == null }) add("${plnTrips.count { it.gross == null }} PLN trips have no gross amount.")
            if (plnTrips.any { it.fees == null }) add("${plnTrips.count { it.fees == null }} PLN trips have no fee value; net is unknown for those trips.")
            if (plnTrips.any { it.bonus == null }) add("${plnTrips.count { it.bonus == null }} PLN trips have no bonus value; net is unknown for those trips.")
            if (plnTrips.any { it.distanceKm == null }) add("${plnTrips.count { it.distanceKm == null }} PLN trips have no distance; vehicle cost is unknown for those trips.")
            if (plnTrips.any { parseHour(it.occurredAt) == null }) add("${plnTrips.count { parseHour(it.occurredAt) == null }} PLN trips have no parseable time.")
        }

        return DriverAnalytics(
            tripCount = trips.size,
            plnTripCount = plnTrips.size,
            tripsWithGross = plnTrips.count { it.gross != null },
            tripsWithFees = plnTrips.count { it.fees != null },
            tripsWithBonus = plnTrips.count { it.bonus != null },
            tripsWithDistance = plnTrips.count { it.distanceKm != null && it.distanceKm >= 0.0 },
            tripsWithTime = plnTrips.count { parseHour(it.occurredAt) != null },
            estimatedNetTrips = completeNet.size,
            estimatedNetPln = completeNet.takeIf { it.isNotEmpty() }?.sum(),
            platforms = platformStats,
            hours = byHour,
            dataWarnings = warnings
        )
    }

    private fun parseHour(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        val text = value.trim()
        return try {
            Instant.parse(text).atZone(java.time.ZoneId.systemDefault()).hour
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(text).hour
            } catch (_: Exception) {
                try {
                    LocalDateTime.parse(text).hour
                } catch (_: Exception) {
                    try {
                        LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).hour
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }
    }
}
