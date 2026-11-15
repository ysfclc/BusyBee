package com.ysfclc.busybee.plaintextpoc

/**
 * Stable, app-owned plaintext observation points for this POC only.
 *
 * outbound() is called after JSON serialisation and immediately before the HTTPS
 * transport receives the body. inbound() is called after HttpsURLConnection has
 * completed TLS decryption/body reading and before JSON parsing/state handling.
 *
 * The methods intentionally do not log or persist payloads. The Frida script in
 * ../instrumentation hooks only these methods in this owned package.
 */
object PlaintextBoundary {
    @JvmStatic fun outbound(json: String) { /* observation hook target */ }
    @JvmStatic fun inbound(json: String) { /* observation hook target */ }
}
