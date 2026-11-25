package com.ysfclc.busybee.drivercommandcentre

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class DriverMoneyPeriod(val label: String, val days: Long?) {
    TODAY("Today", 1),
    SEVEN_DAYS("7 days", 7),
    THIRTY_DAYS("30 days", 30),
    ALL_TIME("All time", null)
}

data class DriverMoneyReport(
    val period: DriverMoneyPeriod,
    val recordCount: Int,
    val tripCount: Int,
    val datedTripCount: Int,
    val undatedTripCount: Int,
    val nonPlnTripCount: Int,
    val grossPln: Double?,
    val feesPln: Double?,
    val bonusesPln: Double?,
    val knownNetBeforeVehiclePln: Double?,
    val estimatedVehicleCostPln: Double?,
    val estimatedNetAfterVehiclePln: Double?,
    val completeNetTrips: Int,
    val tripsWithGross: Int,
    val tripsWithFees: Int,
    val tripsWithBonus: Int,
    val tripsWithDistance: Int,
    val analytics: DriverAnalytics
)

/**
 * Period-aware, evidence-conservative money reporting.
 *
 * Unknown timestamps are included only in All time. Finite periods never guess
 * where an undated record belongs. Currency is never converted or mixed.
 */
object DriverMoneyEngine {
    private val tripTypes = setOf("trip", "ride", "completed_trip")

    fun calculate(
        records: List<DriverRecord>,
        vehicleCostPerKm: Double,
        period: DriverMoneyPeriod,
        today: LocalDate = LocalDate.now(ZoneId.systemDefault()),
        zone: ZoneId = ZoneId.systemDefault()
    ): DriverMoneyReport {
        require(vehicleCostPerKm.isFinite() && vehicleCostPerKm >= 0.0) {
            "Vehicle cost must be a non-negative number"
        }
        val trips = records.filter { it.type.trim().lowercase(Locale.ROOT) in tripTypes }
        val undated = trips.count { parseDate(it.occurredAt, zone) == null }
        val dated = records.mapNotNull { record ->
            parseDate(record.occurredAt, zone)?.let { it to record }
        }
        val start = period.days?.let { today.minusDays(it - 1) }
        val selectedRecords = when (period) {
            DriverMoneyPeriod.ALL_TIME -> records
            else -> dated.filter { (date, _) -> date >= start && date <= today }.map { it.second }
        }
        val selectedTrips = selectedRecords.filter { it.type.trim().lowercase(Locale.ROOT) in tripTypes }
        val pln = selectedTrips.filter { it.currency.equals("PLN", ignoreCase = true) }
        val completeAmounts = pln.filter { it.gross != null && it.fees != null && it.bonus != null }
        val completeVehicle = completeAmounts.filter { it.distanceKm != null && it.distanceKm >= 0.0 }
        val distances = pln.mapNotNull { it.distanceKm?.takeIf { km -> km >= 0.0 } }
        val analytics = DriverAnalyticsEngine.calculate(selectedRecords, vehicleCostPerKm)

        return DriverMoneyReport(
            period = period,
            recordCount = selectedRecords.size,
            tripCount = selectedTrips.size,
            datedTripCount = selectedTrips.count { parseDate(it.occurredAt, zone) != null },
            undatedTripCount = undated,
            nonPlnTripCount = selectedTrips.size - pln.size,
            grossPln = pln.mapNotNull { it.gross }.takeIf { it.isNotEmpty() }?.sum(),
            feesPln = pln.mapNotNull { it.fees }.takeIf { it.isNotEmpty() }?.sum(),
            bonusesPln = pln.mapNotNull { it.bonus }.takeIf { it.isNotEmpty() }?.sum(),
            knownNetBeforeVehiclePln = completeAmounts.map { it.gross!! - it.fees!! + it.bonus!! }
                .takeIf { it.isNotEmpty() }?.sum(),
            estimatedVehicleCostPln = distances.sum().takeIf { distances.isNotEmpty() }?.times(vehicleCostPerKm),
            estimatedNetAfterVehiclePln = completeVehicle.map {
                it.gross!! - it.fees!! + it.bonus!! - it.distanceKm!! * vehicleCostPerKm
            }.takeIf { it.isNotEmpty() }?.sum(),
            completeNetTrips = completeVehicle.size,
            tripsWithGross = pln.count { it.gross != null },
            tripsWithFees = pln.count { it.fees != null },
            tripsWithBonus = pln.count { it.bonus != null },
            tripsWithDistance = distances.size,
            analytics = analytics
        )
    }

    fun parseDate(value: String?, zone: ZoneId = ZoneId.systemDefault()): LocalDate? {
        if (value.isNullOrBlank()) return null
        val text = value.trim()
        return try {
            OffsetDateTime.parse(text).atZoneSameInstant(zone).toLocalDate()
        } catch (_: Exception) {
            try {
                Instant.parse(text).atZone(zone).toLocalDate()
            } catch (_: Exception) {
                try {
                    LocalDateTime.parse(text).toLocalDate()
                } catch (_: Exception) {
                    try {
                        LocalDate.parse(text)
                    } catch (_: Exception) {
                        try {
                            LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).toLocalDate()
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }
        }
    }
}
