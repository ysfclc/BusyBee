package com.ysfclc.appobserver

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

/** Screenshot-first rule authoring with explicit, user-selected content sources. */
class SemanticRuleActivity : Activity() {
    private lateinit var screenshot: ImageView
    private lateinit var overlay: SelectionOverlay
    private lateinit var labelInput: EditText
    private lateinit var sourceSpinner: Spinner
    private lateinit var scopeSpinner: Spinner
    private var bitmapWidth = 0
    private var bitmapHeight = 0
    private var selectedRegion: Rect? = null
    private var screenshotId = ""
    private var targetPackage = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_IMAGE_PATH)
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        screenshotId = intent.getStringExtra(EXTRA_SCREENSHOT_ID).orEmpty()
        val bitmap = path?.let { BitmapFactory.decodeFile(it) }
        if (bitmap == null || targetPackage.isBlank()) {
            Toast.makeText(this, "The screenshot is no longer available.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        bitmapWidth = bitmap.width
        bitmapHeight = bitmap.height

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(Color.rgb(9, 17, 28))
        }
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "DEFINE WHAT THIS REGION MEANS"
            textSize = 18f
            setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = "Draw around a screen region. Give it a meaning such as chat, notification bar, price, map, or any label you choose."
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(8), 0, dp(12))
        })

        labelInput = EditText(this).apply {
            hint = "Meaning / category (e.g. chat messages)"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        root.addView(labelInput)

        sourceSpinner = Spinner(this)
        sourceSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item,
            listOf(
                "Visual screenshot only",
                "Read UI text inside this region",
                "Link selected app notification content"
            )
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        root.addView(sourceSpinner)

        scopeSpinner = Spinner(this)
        scopeSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item,
            listOf("Scope: selected app", "Scope: Android system surfaces (status bar / notification shade)")
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        root.addView(scopeSpinner)

        root.addView(TextView(this).apply {
            text = "Content is never read unless you choose one of the two explicit content sources. Visual-only rules retain the screenshot and region label without extracting text. Password fields are always excluded. System-surface text comes from notification listener rules; the observer does not scrape all system windows."
            textSize = 12f
            setTextColor(Color.rgb(255, 202, 121))
            setPadding(0, dp(8), 0, dp(10))
        })

        val stage = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(450)
            )
        }
        screenshot = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(bitmap)
            contentDescription = "Screenshot for semantic rule annotation"
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        overlay = SelectionOverlay(this) { viewRect ->
            selectedRegion = mapToBitmapRect(viewRect)
            if (selectedRegion == null) {
                Toast.makeText(this, "Draw a larger region over the screenshot.", Toast.LENGTH_SHORT).show()
            }
        }
        stage.addView(screenshot)
        stage.addView(overlay)
        root.addView(stage)
        root.addView(TextView(this).apply {
            text = "Drag on the screenshot to select a rectangle. Saved rules stay on this device and can be reused in later sessions."
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(8), 0, dp(10))
        })
        root.addView(Button(this).apply {
            text = "SAVE MEANING RULE"
            setOnClickListener { saveRule() }
        })
        root.addView(Button(this).apply {
            text = "CANCEL"
            setOnClickListener { finish() }
        })
    }

    private fun mapToBitmapRect(viewRect: RectF): Rect? {
        if (bitmapWidth <= 0 || bitmapHeight <= 0) return null
        // ImageView uses FIT_CENTER; map coordinates using the same letterboxed transform
        // that the screenshot viewer draws, rather than ImageView.imageMatrix (which is
        // not the draw matrix for non-MATRIX scale types).
        val viewWidth = screenshot.width.toFloat()
        val viewHeight = screenshot.height.toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f) return null
        val scale = minOf(viewWidth / bitmapWidth, viewHeight / bitmapHeight)
        if (!scale.isFinite() || scale <= 0f) return null
        val leftInset = (viewWidth - bitmapWidth * scale) / 2f
        val topInset = (viewHeight - bitmapHeight * scale) / 2f
        val left = ((viewRect.left - leftInset) / scale).coerceIn(0f, bitmapWidth.toFloat()).toInt()
        val top = ((viewRect.top - topInset) / scale).coerceIn(0f, bitmapHeight.toFloat()).toInt()
        val right = ((viewRect.right - leftInset) / scale).coerceIn(0f, bitmapWidth.toFloat()).toInt()
        val bottom = ((viewRect.bottom - topInset) / scale).coerceIn(0f, bitmapHeight.toFloat()).toInt()
        return Rect(left, top, right, bottom).takeIf { it.width() >= 4 && it.height() >= 4 }
    }

    private fun saveRule() {
        val source = when (sourceSpinner.selectedItemPosition) {
            1 -> SemanticRule.SOURCE_ACCESSIBILITY_TEXT
            2 -> SemanticRule.SOURCE_NOTIFICATION_TEXT
            else -> SemanticRule.SOURCE_VISUAL_ONLY
        }
        val region = selectedRegion
        if (region == null) {
            Toast.makeText(this, "Draw a region on the screenshot first.", Toast.LENGTH_SHORT).show()
            return
        }
        val rulePackage = if (scopeSpinner.selectedItemPosition == 1) {
            SemanticRuleStore.SYSTEM_SURFACE_PACKAGE
        } else targetPackage
        if (rulePackage == SemanticRuleStore.SYSTEM_SURFACE_PACKAGE &&
            source == SemanticRule.SOURCE_ACCESSIBILITY_TEXT) {
            Toast.makeText(this, "System surfaces currently support visual rules or linked notification payloads.", Toast.LENGTH_LONG).show()
            return
        }
        runCatching {
            SemanticRuleStore.add(
                this, rulePackage, labelInput.text.toString(), source,
                region!!, bitmapWidth, bitmapHeight, screenshotId
            )
        }.onSuccess {
            Toast.makeText(this, "Meaning rule saved.", Toast.LENGTH_LONG).show()
            setResult(RESULT_OK)
            finish()
        }.onFailure {
            Toast.makeText(this, it.message ?: "Could not save the rule.", Toast.LENGTH_LONG).show()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private class SelectionOverlay(
        context: android.content.Context,
        private val onSelected: (RectF) -> Unit
    ) : View(context) {
        private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(65, 230, 185)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x3341E6B9
            style = Paint.Style.FILL
        }
        private var startX = 0f
        private var startY = 0f
        private var endX = 0f
        private var endY = 0f
        private var drawing = false

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (drawing || (endX > startX && endY > startY)) {
                val rect = RectF(minOf(startX, endX), minOf(startY, endY),
                    maxOf(startX, endX), maxOf(startY, endY))
                canvas.drawRect(rect, fill)
                canvas.drawRect(rect, outline)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x; startY = event.y
                    endX = startX; endY = startY
                    drawing = true
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    endX = event.x; endY = event.y
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    endX = event.x; endY = event.y
                    drawing = false
                    invalidate()
                    val rect = RectF(minOf(startX, endX), minOf(startY, endY),
                        maxOf(startX, endX), maxOf(startY, endY))
                    if (rect.width() > 12f && rect.height() > 12f) onSelected(rect)
                    performClick()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    drawing = false
                    invalidate()
                    return true
                }
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }

    companion object {
        const val EXTRA_IMAGE_PATH = "screenshot_image_path"
        const val EXTRA_PACKAGE = "screenshot_target_package"
        const val EXTRA_SCREENSHOT_ID = "screenshot_capture_id"
    }
}
