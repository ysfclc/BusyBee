package com.ysfclc.busybee.plaintextpoc

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var endpoint: EditText
    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        endpoint = EditText(this).apply {
            setText("https://localhost:8443/event")
            hint = "Owned HTTPS fixture URL"
            singleLine = true
        }
        output = TextView(this).apply { textSize = 14f; text = "TEST APP ONLY — synthetic data; no provider accounts." }
        val run = Button(this).apply {
            text = "Send synthetic offer lifecycle"
            setOnClickListener { runLifecycle() }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            addView(TextView(this@MainActivity).apply {
                text = "BusyBee Plaintext Observer POC\nOutbound = before HTTPS transport\nInbound = after TLS body decryption"
                textSize = 18f
            })
            addView(endpoint, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(run)
            addView(ScrollView(this@MainActivity).apply { addView(output) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
    }

    private fun runLifecycle() {
        val base = endpoint.text.toString().trim()
        output.text = "Running synthetic HTTPS lifecycle…"
        worker.execute {
            val log = StringBuilder()
            val events = listOf("offer_lookup", "offer_update", "offer_expired")
            try {
                events.forEachIndexed { index, type ->
                    val request = JSONObject()
                        .put("event_type", type)
                        .put("correlation_id", "poc-offer-0001")
                        .put("sequence", index + 1)
                        .put("synthetic_secret", "NEVER_PERSIST_THIS")
                        .put("synthetic_offer_id", "synthetic-ride-001")
                        .toString()

                    // Exact outbound plaintext point: serialised body, before TLS transport.
                    PlaintextBoundary.outbound(request)
                    val connection = (URL(base).openConnection() as HttpsURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 5_000
                        readTimeout = 5_000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    }
                    val response = try {
                        connection.outputStream.use { it.write(request.toByteArray(Charsets.UTF_8)) }
                        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
                        stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    } finally {
                        connection.disconnect()
                    }

                    // Exact inbound plaintext point: HTTPS stack has decrypted the body;
                    // app has not yet parsed or applied the returned event.
                    PlaintextBoundary.inbound(response)
                    val parsed = JSONObject(response)
                    log.append("HTTP event ").append(index + 1).append(": ")
                        .append(parsed.optString("event_type", "unknown"))
                        .append(" / ").append(parsed.optString("offer_id", "no offer id"))
                        .append("\n")
                }
                log.append("\nCompleted. Check the Frida console for outbound/inbound boundary observations.")
            } catch (error: Exception) {
                log.append("FAILED: ").append(error.javaClass.simpleName)
                    .append(": ").append(error.message ?: "no detail")
                    .append("\nCheck endpoint URL, test CA installation and local fixture server.")
            }
            runOnUiThread { output.text = log.toString() }
        }
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}
