package com.ysfclc.busybee.drivercommandcentre

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import java.util.Locale

internal enum class MapEventMarkerKind { ROADWORKS, HAZARD, CLOSURE, WEATHER, OTHER }
internal enum class MapControlIcon { MENU, RECENTER, REPORT }

internal object MapIconPolicy {
    fun eventKind(category: String): MapEventMarkerKind {
        val value = category.trim().lowercase(Locale.ROOT)
        return when {
            value.contains("roadwork") || value.contains("construction") -> MapEventMarkerKind.ROADWORKS
            value.contains("closure") || value.contains("closed") -> MapEventMarkerKind.CLOSURE
            value.contains("hazard") || value.contains("crash") || value.contains("accident") -> MapEventMarkerKind.HAZARD
            value.contains("weather") -> MapEventMarkerKind.WEATHER
            else -> MapEventMarkerKind.OTHER
        }
    }
}

/** One coherent, drawn icon family for map events, routes, controls and workspace navigation. */
internal object MapIconArtwork {
    private val mint = Color.rgb(39, 217, 162)
    private val amber = Color.rgb(255, 183, 77)
    private val red = Color.rgb(255, 103, 96)
    private val blue = Color.rgb(103, 190, 245)
    private val ink = Color.rgb(13, 35, 43)

    private fun bitmap(context: Context, sizeDp: Int, draw: (Canvas) -> Unit): Bitmap {
        val density = context.resources.displayMetrics.density
        val size = (sizeDp * density).toInt().coerceAtLeast(1)
        val image = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.scale(density, density)
        draw(canvas)
        return image
    }

    private fun paint(color: Int, style: Paint.Style = Paint.Style.FILL, width: Float = 1.8f) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.style = style
            strokeWidth = width
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

    private fun markerBase(canvas: Canvas, night: Boolean, accent: Int) {
        canvas.drawCircle(29f, 30f, 26f, paint(0x55000000))
        canvas.drawCircle(29f, 28f, 24f, paint(if (night) 0xFF102D36.toInt() else 0xFFF7FBF8.toInt()))
        canvas.drawCircle(29f, 28f, 23f, paint(accent, Paint.Style.STROKE, 1.6f))
    }

    fun roadEvent(context: Context, category: String, night: Boolean): Bitmap {
        val kind = MapIconPolicy.eventKind(category)
        val accent = when (kind) {
            MapEventMarkerKind.ROADWORKS -> amber
            MapEventMarkerKind.CLOSURE -> red
            MapEventMarkerKind.HAZARD -> red
            MapEventMarkerKind.WEATHER -> blue
            MapEventMarkerKind.OTHER -> mint
        }
        return bitmap(context, 58) { canvas ->
            markerBase(canvas, night, accent)
            when (kind) {
                MapEventMarkerKind.ROADWORKS -> {
                    val cone = Path().apply { moveTo(29f, 13f); lineTo(42f, 42f); lineTo(16f, 42f); close() }
                    canvas.drawPath(cone, paint(accent))
                    canvas.drawLine(22f, 31f, 36f, 31f, paint(Color.WHITE, Paint.Style.STROKE, 3f))
                    canvas.drawRoundRect(RectF(14f, 42f, 44f, 46f), 2f, 2f, paint(Color.WHITE))
                }
                MapEventMarkerKind.HAZARD -> {
                    val triangle = Path().apply { moveTo(29f, 12f); lineTo(45f, 42f); lineTo(13f, 42f); close() }
                    canvas.drawPath(triangle, paint(accent))
                    canvas.drawLine(29f, 22f, 29f, 32f, paint(Color.WHITE, Paint.Style.STROKE, 3.2f))
                    canvas.drawCircle(29f, 37f, 1.9f, paint(Color.WHITE))
                }
                MapEventMarkerKind.CLOSURE -> {
                    canvas.drawRoundRect(RectF(13f, 20f, 45f, 38f), 4f, 4f, paint(accent))
                    val stripe = paint(Color.WHITE, Paint.Style.STROKE, 3f)
                    canvas.drawLine(19f, 22f, 14f, 30f, stripe)
                    canvas.drawLine(29f, 22f, 24f, 30f, stripe)
                    canvas.drawLine(39f, 22f, 34f, 30f, stripe)
                    canvas.drawLine(17f, 41f, 41f, 41f, paint(Color.WHITE, Paint.Style.STROKE, 2.5f))
                }
                MapEventMarkerKind.WEATHER -> {
                    val cloud = paint(Color.WHITE)
                    canvas.drawCircle(23f, 29f, 7f, cloud)
                    canvas.drawCircle(32f, 25f, 9f, cloud)
                    canvas.drawCircle(39f, 30f, 6f, cloud)
                    canvas.drawRoundRect(RectF(18f, 29f, 44f, 37f), 4f, 4f, cloud)
                    val rain = paint(blue, Paint.Style.STROKE, 2.4f)
                    canvas.drawLine(23f, 40f, 20f, 45f, rain)
                    canvas.drawLine(33f, 40f, 30f, 45f, rain)
                    canvas.drawLine(42f, 40f, 39f, 45f, rain)
                }
                MapEventMarkerKind.OTHER -> {
                    canvas.drawCircle(29f, 28f, 10f, paint(accent, Paint.Style.STROKE, 3f))
                    canvas.drawCircle(29f, 28f, 3f, paint(Color.WHITE))
                }
            }
        }
    }

    fun driverReport(context: Context, night: Boolean): Bitmap = bitmap(context, 58) { canvas ->
        markerBase(canvas, night, mint)
        val bubble = Path().apply {
            moveTo(17f, 17f); lineTo(41f, 17f); quadTo(44f, 17f, 44f, 20f)
            lineTo(44f, 34f); quadTo(44f, 37f, 41f, 37f); lineTo(29f, 37f)
            lineTo(21f, 44f); lineTo(22f, 37f); lineTo(17f, 37f)
            quadTo(14f, 37f, 14f, 34f); lineTo(14f, 20f); quadTo(14f, 17f, 17f, 17f); close()
        }
        canvas.drawPath(bubble, paint(mint))
        listOf(23f, 29f, 35f).forEach { canvas.drawCircle(it, 27f, 1.8f, paint(ink)) }
    }

    fun routeEndpoint(context: Context, destination: Boolean, night: Boolean): Bitmap = bitmap(context, 58) { canvas ->
        val accent = if (destination) amber else mint
        markerBase(canvas, night, accent)
        if (!destination) {
            canvas.drawCircle(29f, 28f, 11f, paint(accent, Paint.Style.STROKE, 3f))
            canvas.drawCircle(29f, 28f, 5f, paint(Color.WHITE))
        } else {
            canvas.drawLine(23f, 14f, 23f, 44f, paint(Color.WHITE, Paint.Style.STROKE, 2.8f))
            val flag = Path().apply { moveTo(25f, 16f); lineTo(43f, 20f); lineTo(25f, 27f); close() }
            canvas.drawPath(flag, paint(accent))
            canvas.drawCircle(23f, 45f, 2.5f, paint(Color.WHITE))
        }
    }

    fun control(context: Context, kind: MapControlIcon): Bitmap = bitmap(context, 24) { canvas ->
        val p = paint(Color.WHITE, Paint.Style.STROKE, 2.1f)
        when (kind) {
            MapControlIcon.MENU -> {
                canvas.drawLine(4f, 6f, 20f, 6f, p)
                canvas.drawLine(4f, 12f, 20f, 12f, p)
                canvas.drawLine(4f, 18f, 20f, 18f, p)
            }
            MapControlIcon.RECENTER -> {
                canvas.drawCircle(12f, 12f, 7.5f, p)
                canvas.drawCircle(12f, 12f, 2f, paint(mint))
                canvas.drawLine(12f, 1.5f, 12f, 4f, p)
                canvas.drawLine(12f, 20f, 12f, 22.5f, p)
                canvas.drawLine(1.5f, 12f, 4f, 12f, p)
                canvas.drawLine(20f, 12f, 22.5f, 12f, p)
            }
            MapControlIcon.REPORT -> {
                val pin = Path().apply { moveTo(12f, 22f); cubicTo(10f, 18f, 4f, 13f, 4f, 9f); cubicTo(4f, 3f, 20f, 3f, 20f, 9f); cubicTo(20f, 13f, 14f, 18f, 12f, 22f); close() }
                canvas.drawPath(pin, p)
                canvas.drawLine(12f, 6.5f, 12f, 11f, p)
                canvas.drawCircle(12f, 14.5f, 0.9f, paint(Color.WHITE))
            }
        }
    }

    fun tab(context: Context, tab: String): Bitmap = bitmap(context, 22) { canvas ->
        val p = paint(Color.WHITE, Paint.Style.STROKE, 1.9f)
        when (tab.uppercase(Locale.ROOT)) {
            "MAP" -> {
                val map = Path().apply { moveTo(3f, 5f); lineTo(8.5f, 3f); lineTo(14f, 5f); lineTo(19f, 3f); lineTo(19f, 17f); lineTo(14f, 19f); lineTo(8.5f, 17f); lineTo(3f, 19f); close() }
                canvas.drawPath(map, p); canvas.drawLine(8.5f, 3f, 8.5f, 17f, p); canvas.drawLine(14f, 5f, 14f, 19f, p)
            }
            "OFFERS" -> {
                val ticket = Path().apply { moveTo(4f, 5f); lineTo(18f, 5f); lineTo(18f, 9f); quadTo(14f, 11f, 18f, 13f); lineTo(18f, 17f); lineTo(4f, 17f); lineTo(4f, 13f); quadTo(8f, 11f, 4f, 9f); close() }
                canvas.drawPath(ticket, p); canvas.drawLine(10f, 7f, 10f, 15f, p)
            }
            "EARNINGS" -> {
                canvas.drawLine(4f, 18f, 4f, 4f, p); canvas.drawLine(4f, 18f, 20f, 18f, p)
                canvas.drawRoundRect(RectF(7f, 11f, 10f, 18f), 1f, 1f, paint(Color.WHITE))
                canvas.drawRoundRect(RectF(12f, 7f, 15f, 18f), 1f, 1f, paint(Color.WHITE))
                canvas.drawRoundRect(RectF(17f, 9f, 20f, 18f), 1f, 1f, paint(Color.WHITE))
            }
            "DRIVER HUB" -> {
                canvas.drawRoundRect(RectF(3f, 8f, 19f, 16f), 3f, 3f, p)
                canvas.drawRoundRect(RectF(6f, 5f, 16f, 10f), 2f, 2f, p)
                canvas.drawCircle(7f, 17f, 1.7f, paint(Color.WHITE)); canvas.drawCircle(15f, 17f, 1.7f, paint(Color.WHITE))
            }
            else -> {
                canvas.drawLine(6f, 6f, 16f, 16f, p); canvas.drawLine(16f, 6f, 6f, 16f, p)
                canvas.drawCircle(5f, 5f, 2.8f, p); canvas.drawCircle(17f, 5f, 2.8f, p); canvas.drawCircle(5f, 17f, 2.8f, p); canvas.drawCircle(17f, 17f, 2.8f, p)
            }
        }
    }
}
