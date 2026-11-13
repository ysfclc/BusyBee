package com.ysfclc.busybee.drivercommandcentre

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

internal enum class DaylightPhase { DAY, DUSK, NIGHT }

internal data class BusyBeePalette(
    val background: Int,
    val panel: Int,
    val card: Int,
    val primaryText: Int,
    val mutedText: Int,
    val accent: Int,
    val warning: Int,
    val stroke: Int,
    val field: Int,
    val secondary: Int,
    val overlay: Int,
    val selected: Int,
    val actionText: Int,
    val divider: Int,
    val mapStyleUri: String
)

/**
 * Calculates the sun's elevation for the driver's last known coordinates.
 * This intentionally uses no location lookup of its own: callers pass only a
 * location that Android has already granted to BusyBee.
 */
internal object SolarLighting {
    private const val DEG_TO_RAD = Math.PI / 180.0

    fun phase(latitude: Double?, longitude: Double?, epochMillis: Long): DaylightPhase {
        if (latitude == null || longitude == null ||
            !latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0
        ) {
            return localClockFallback(epochMillis)
        }
        val elevation = solarElevationDegrees(latitude, longitude, epochMillis)
        return when {
            elevation >= 8.0 -> DaylightPhase.DAY
            elevation >= -6.0 -> DaylightPhase.DUSK
            else -> DaylightPhase.NIGHT
        }
    }

    fun solarElevationDegrees(latitude: Double, longitude: Double, epochMillis: Long): Double {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)

        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = epochMillis
        }
        val day = utc.get(Calendar.DAY_OF_YEAR)
        val hour = utc.get(Calendar.HOUR_OF_DAY) +
            utc.get(Calendar.MINUTE) / 60.0 +
            utc.get(Calendar.SECOND) / 3600.0

        val gamma = 2.0 * Math.PI / 365.0 * (day - 1 + (hour - 12.0) / 24.0)
        val equationOfTime = 229.18 * (
            0.000075 +
                0.001868 * cos(gamma) -
                0.032077 * sin(gamma) -
                0.014615 * cos(2.0 * gamma) -
                0.040849 * sin(2.0 * gamma)
            )
        val declination =
            0.006918 -
                0.399912 * cos(gamma) +
                0.070257 * sin(gamma) -
                0.006758 * cos(2.0 * gamma) +
                0.000907 * sin(2.0 * gamma) -
                0.002697 * cos(3.0 * gamma) +
                0.001480 * sin(3.0 * gamma)

        val utcMinutes = utc.get(Calendar.HOUR_OF_DAY) * 60.0 +
            utc.get(Calendar.MINUTE) +
            utc.get(Calendar.SECOND) / 60.0
        val solarMinutes = ((utcMinutes + equationOfTime + 4.0 * longitude) % 1440.0 + 1440.0) % 1440.0
        val hourAngle = (solarMinutes / 4.0 - 180.0) * DEG_TO_RAD
        val latitudeRadians = latitude * DEG_TO_RAD
        val sinElevation = sin(latitudeRadians) * sin(declination) +
            cos(latitudeRadians) * cos(declination) * cos(hourAngle)
        return Math.toDegrees(asin(sinElevation.coerceIn(-1.0, 1.0)))
    }

    private fun localClockFallback(epochMillis: Long): DaylightPhase {
        val localTime = Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
        return when {
            !localTime.isBefore(LocalTime.of(7, 0)) && localTime.isBefore(LocalTime.of(18, 30)) ->
                DaylightPhase.DAY
            (!localTime.isBefore(LocalTime.of(5, 0)) && localTime.isBefore(LocalTime.of(7, 0))) ||
                (!localTime.isBefore(LocalTime.of(18, 30)) && localTime.isBefore(LocalTime.of(20, 30))) ->
                DaylightPhase.DUSK
            else -> DaylightPhase.NIGHT
        }
    }
}

internal object BusyBeePalettes {
    fun forPhase(phase: DaylightPhase): BusyBeePalette = when (phase) {
        DaylightPhase.DAY -> BusyBeePalette(
            background = 0xFFF3F7F4.toInt(),
            panel = 0xFFFFFFFF.toInt(),
            card = 0xFFFFFFFF.toInt(),
            primaryText = 0xFF172D35.toInt(),
            mutedText = 0xFF5C7279.toInt(),
            accent = 0xFF087A5A.toInt(),
            warning = 0xFFB46A1D.toInt(),
            stroke = 0xFFD6E2DC.toInt(),
            field = 0xFFEDF3EF.toInt(),
            secondary = 0xFFE4EEE8.toInt(),
            overlay = 0xF2FFFFFF.toInt(),
            selected = 0xFFDCEFE5.toInt(),
            actionText = 0xFFFFFFFF.toInt(),
            divider = 0xFFDCE5DF.toInt(),
            mapStyleUri = "https://tiles.openfreemap.org/styles/bright"
        )
        DaylightPhase.DUSK -> BusyBeePalette(
            background = 0xFF17222D.toInt(),
            panel = 0xFF202E3A.toInt(),
            card = 0xFF283846.toInt(),
            primaryText = 0xFFF1EBDD.toInt(),
            mutedText = 0xFFB0BAC1.toInt(),
            accent = 0xFFE8B66F.toInt(),
            warning = 0xFFFFB56D.toInt(),
            stroke = 0xFF455663.toInt(),
            field = 0xFF31414D.toInt(),
            secondary = 0xFF3A4A56.toInt(),
            overlay = 0xF2202E3A.toInt(),
            selected = 0xFF3A4D59.toInt(),
            actionText = 0xFF17222D.toInt(),
            divider = 0xFF43535F.toInt(),
            mapStyleUri = "https://tiles.openfreemap.org/styles/fiord"
        )
        DaylightPhase.NIGHT -> BusyBeePalette(
            background = 0xFF07131B.toInt(),
            panel = 0xFF0C1C26.toInt(),
            card = 0xFF10232E.toInt(),
            primaryText = 0xFFF4FAFA.toInt(),
            mutedText = 0xFF9AAEB8.toInt(),
            accent = 0xFF8FE7C1.toInt(),
            warning = 0xFFFFC978.toInt(),
            stroke = 0xFF203743.toInt(),
            field = 0xFF1B303A.toInt(),
            secondary = 0xFF213845.toInt(),
            overlay = 0xE610232E.toInt(),
            selected = 0xFF1B343B.toInt(),
            actionText = 0xFF07131B.toInt(),
            divider = 0xFF263943.toInt(),
            mapStyleUri = "https://tiles.openfreemap.org/styles/dark"
        )
    }
}
