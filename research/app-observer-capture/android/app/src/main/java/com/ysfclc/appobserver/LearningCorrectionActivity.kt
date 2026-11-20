package com.ysfclc.appobserver

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** Optional correction surface: feedback trains the on-device classifier, not region rectangles. */
class LearningCorrectionActivity : Activity() {
    private var latest: JSONObject? = null
    private var featureVector: List<String> = emptyList()
    private var targetPackage = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        latest = readLatestUnderstanding()
        val record = latest
        if (record == null) {
            Toast.makeText(this, "Capture a screen before reviewing its machine identification.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        targetPackage = record.optString("packageName")
        val features = record.optJSONArray("learningFeatures")
        featureVector = if (features == null) emptyList() else buildList {
            for (index in 0 until features.length()) {
                features.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
        if (targetPackage.isBlank() || featureVector.isEmpty()) {
            Toast.makeText(this, "This observation has no usable learning features.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
            setBackgroundColor(Color.rgb(9, 17, 28))
        }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        root.addView(TextView(this).apply {
            text = "TEACH THE ON-DEVICE MODEL"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Use this only when the automatic identification is wrong or incomplete. The correction trains a local model for this app and helps it recognise similar screens next time. It does not create a fixed screen-region rule."
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(8), 0, dp(12))
        })

        val screenshotId = record.optString("screenshotId")
        val screenshot = ObservationStore.screenshots(this, 120).firstOrNull { it.id == screenshotId }
        if (screenshot != null) {
            root.addView(ImageView(this).apply {
                setImageBitmap(BitmapFactory.decodeFile(screenshot.file.absolutePath))
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                background = GradientDrawable().apply {
                    setColor(Color.BLACK)
                    cornerRadius = dp(10).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(330)
                )
            })
        }
        val state = record.optJSONObject("state")
        root.addView(TextView(this).apply {
            text = "Current machine state: " + (state?.optString("label") ?: "unclassified") +
                "\nTarget: " + targetPackage +
                "\nLearning features: " + featureVector.size
            textSize = 12f
            setTextColor(Color.rgb(157, 230, 211))
            setPadding(0, dp(10), 0, dp(12))
        })

        val predictions = record.optJSONArray("concepts") ?: org.json.JSONArray()
        root.addView(TextView(this).apply {
            text = buildString {
                append("MACHINE'S CURRENT IDENTIFICATIONS\n")
                if (predictions.length() == 0) append("No confident category was found.")
                for (index in 0 until predictions.length()) {
                    val item = predictions.optJSONObject(index) ?: continue
                    append("• ").append(item.optString("concept"))
                        .append(" · ").append((item.optDouble("confidence") * 100).toInt())
                        .append("%\n")
                }
            }
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(14))
        })

        val conceptValues = MultimodalInference.concepts
        val picker = Spinner(this)
        picker.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item,
            conceptValues.map { it.replace('_', ' ').replaceFirstChar { first -> first.titlecase(Locale.getDefault()) } }
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val topPrediction = predictions.optJSONObject(0)?.optString("concept")
        val selectedIndex = conceptValues.indexOf(topPrediction)
        picker.setSelection(if (selectedIndex >= 0) selectedIndex else 0)
        root.addView(TextView(this).apply {
            text = "What does this screen actually represent?"
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(6))
        })
        root.addView(picker)
        root.addView(TextView(this).apply {
            text = "Or teach a new concept name"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(12), 0, dp(4))
        })
        val customConceptInput = EditText(this).apply {
            hint = "Custom concept (optional)"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        root.addView(customConceptInput)
        root.addView(TextView(this).apply {
            text = "Custom concepts become learnable labels for this app. The classifier starts using them after two reviewed examples per category across at least two categories."
            textSize = 11f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(4), 0, dp(10))
        })
        root.addView(Button(this).apply {
            text = "SAVE CORRECTION TO LOCAL MODEL"
            setOnClickListener {
                val customLabel = customConceptInput.text.toString()
                val concept = if (customLabel.isBlank()) {
                    conceptValues[picker.selectedItemPosition]
                } else {
                    LocalLearningMemory.normaliseConceptLabel(customLabel)
                }
                runCatching {
                    LocalLearningMemory.train(this@LearningCorrectionActivity, targetPackage, concept, featureVector)
                }.onSuccess {
                    Toast.makeText(
                        this@LearningCorrectionActivity,
                        "Training example saved on this device.",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }.onFailure {
                    Toast.makeText(
                        this@LearningCorrectionActivity,
                        it.message ?: "Could not train the local model.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        })
        root.addView(Button(this).apply {
            text = "CANCEL"
            setOnClickListener { finish() }
        })
    }

    private fun readLatestUnderstanding(): JSONObject? {
        val directory = ObservationStore.latestDirectory(this) ?: return null
        val file = File(directory, "screen-understanding.jsonl")
        if (!file.isFile) return null
        return runCatching {
            file.useLines { lines ->
                lines.filter { it.isNotBlank() }.toList().lastOrNull()?.let(::JSONObject)
            }
        }.getOrNull()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
