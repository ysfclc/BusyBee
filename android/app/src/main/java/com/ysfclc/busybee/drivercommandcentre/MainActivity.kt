package com.ysfclc.busybee.drivercommandcentre

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var store: DriverMirrorStore
    private lateinit var content: LinearLayout
    private var notice: String? = null
    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) importCsv(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = DriverMirrorStore(this)
        window.statusBarColor = Color.rgb(7, 17, 24)
        window.navigationBarColor = Color.rgb(7, 17, 24)
        val scroll = ScrollView(this).apply { setBackgroundColor(BG); isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
        }
        scroll.addView(content)
        setContentView(scroll)
        render()
    }

    private fun importCsv(uri: Uri) {
        try {
            val name = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else "driver-export.csv"
            } ?: "driver-export.csv"
            val csv = contentResolver.openInputStream(uri)?.use {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText()
            } ?: error("Could not open selected file")
            val rows = DriverCsv.parse(csv)
            require(rows.isNotEmpty()) { "No data rows found in this CSV" }
            val (read, inserted) = store.importRows(name, rows)
            notice = "Imported $inserted new records from $read rows. Duplicate records are not double-counted."
        } catch (e: Exception) {
            notice = "Import failed: ${e.message ?: "invalid file"}"
        }
        render()
    }

    private fun render() {
        content.removeAllViews()
        val s = store.summary()
        content.addView(text("BUSYBEE", 24f, WHITE, true).apply { letterSpacing = .12f })
        content.addView(text("DRIVER DATA MIRROR  /  PRIVATE BY DEFAULT", 10f, MUTED, true).apply {
            setPadding(0, dp(4), 0, dp(18)); letterSpacing = .09f
        })
        notice?.let { message ->
            content.addView(card().apply { addView(text(message, 12f, MINT, false)) })
            notice = null
        }

        val hero = card()
        hero.addView(text("YOUR WORK, IN ONE PLACE", 11f, MINT, true))
        hero.addView(text(if (s.records == 0) "Start with your real records." else "${s.records} records mirrored", 23f, WHITE, true).apply {
            setPadding(0, dp(8), 0, dp(4))
        })
        hero.addView(text(
            if (s.records == 0) "Import an Uber or Bolt statement/export. BusyBee keeps source evidence and builds your independent work history."
            else "${s.platforms.joinToString("  ·  ")}  •  ${s.imports} imports",
            12f, MUTED, false
        ))
        val import = button("＋  IMPORT PLATFORM CSV")
        import.setOnClickListener { importFile.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) }
        hero.addView(import)
        content.addView(hero)

        content.addView(section("RECONCILED TOTALS", "Only imported values. Blank source fields stay unknown."))
        val grid = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        grid.addView(metric("GROSS", money(s.gross)))
        grid.addView(metric("FEES", money(s.fees)))
        grid.addView(metric("BONUSES", money(s.bonuses)))
        content.addView(grid)
        content.addView(card().apply {
            addView(text("KNOWN NET · BEFORE VEHICLE COSTS", 10f, MUTED, true))
            addView(text(money(s.knownNet), 25f, MINT, true).apply { setPadding(0, dp(5), 0, 0) })
            addView(text("Gross − recorded fees + recorded bonuses. Not profit: vehicle costs and missing adjustments are excluded.", 11f, MUTED, false))
        })

        content.addView(section("ACTIVITY MIRROR", "Cross-platform history, not a map of nearby places."))
        val activity = card()
        activity.addView(text("${s.trips} trips     ·     ${s.offers} offers", 17f, WHITE, true))
        activity.addView(text("${s.records - s.trips - s.offers} other / unclassified records", 11f, MUTED, false).apply {
            setPadding(0, dp(4), 0, dp(12))
        })
        val recent = store.recent()
        if (recent.isEmpty()) activity.addView(text("No activity imported yet. Import a platform export to populate your mirror.", 12f, MUTED, false))
        recent.take(12).forEach { record ->
            activity.addView(divider())
            activity.addView(text("${record.platform}  ·  ${record.type.uppercase(Locale.ROOT)}", 12f, WHITE, true).apply {
                setPadding(0, dp(9), 0, dp(3))
            })
            val route = listOfNotNull(record.occurredAt, record.pickup, record.dropoff).joinToString(" → ")
            activity.addView(text(route.ifBlank { record.externalId ?: "Date / route not supplied" }, 10f, MUTED, false))
            val amounts = listOfNotNull(
                record.gross?.let { "Gross ${format(it)}" },
                record.fees?.let { "Fees ${format(it)}" },
                record.bonus?.let { "Bonus ${format(it)}" }
            ).joinToString("  ·  ")
            activity.addView(text(amounts.ifBlank { "Earnings not supplied" }, 11f, MINT, true).apply {
                setPadding(0, dp(3), 0, dp(8))
            })
        }
        content.addView(activity)

        content.addView(section("SOURCE LEDGER", "Import history, raw row evidence and duplicate protection."))
        content.addView(card().apply {
            val history = store.importHistory()
            if (history.isEmpty()) addView(text("No source files yet.", 12f, MUTED, false))
            history.forEach { addView(text("•  $it", 11f, WHITE, false).apply { setPadding(0, dp(5), 0, dp(5)) }) }
            addView(text("Stored in the app's private on-device database. No upload, account connection or background scraping.", 10f, MUTED, false).apply {
                setPadding(0, dp(12), 0, 0)
            })
        })
        content.addView(text("NEXT: validate real provider exports, reconcile statement totals, add vehicle costs, then calculate earnings by hour, zone and platform.", 10f, MUTED, true).apply {
            setPadding(0, dp(18), 0, 0)
        })
    }

    private fun section(title: String, subtitle: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(2), dp(18), dp(2), dp(9))
        addView(text(title, 12f, WHITE, true))
        addView(text(subtitle, 10f, MUTED, false).apply { setPadding(0, dp(3), 0, 0) })
    }

    private fun metric(label: String, value: String) = card().apply {
        layoutParams = LinearLayout.LayoutParams(0, dp(88), 1f).apply { marginEnd = dp(6) }
        addView(text(label, 9f, MUTED, true))
        addView(text(value, 14f, WHITE, true).apply { setPadding(0, dp(9), 0, 0) })
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = rounded(CARD, 17)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) }
    }

    private fun button(label: String) = TextView(this).apply {
        text = label; gravity = Gravity.CENTER; textSize = 12f
        typeface = Typeface.DEFAULT_BOLD; setTextColor(BG)
        background = rounded(MINT, 13); setPadding(dp(12), dp(15), dp(12), dp(15))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(15) }
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(0xFF263943.toInt())
        layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(3) }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }

    private fun money(value: Double) = String.format(Locale.US, "%.2f PLN", value)
    private fun format(value: Double) = String.format(Locale.US, "%.2f", value)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val BG = 0xFF071118.toInt()
        private const val CARD = 0xFF101F28.toInt()
        private const val WHITE = Color.WHITE
        private const val MUTED = 0xFF9AADB6.toInt()
        private const val MINT = 0xFF8FE7C1.toInt()
    }
}