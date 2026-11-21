package com.ysfclc.busybee.drivercommandcentre

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class PlatformNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName.lowercase()
        val platform = when {
            pkg.contains("uber") -> "Uber"
            pkg.contains("bolt") || pkg.contains("taxify") -> "Bolt"
            pkg.contains("freenow") || pkg.contains("mytaxi") -> "FreeNow"
            pkg.contains("itaxi") -> "iTaxi"
            pkg.contains("cabify") -> "Cabify"
            pkg.contains("lyft") -> "Lyft"
            else -> return
        }
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val body = listOf(
            extras.getCharSequence("android.text")?.toString(),
            extras.getCharSequence("android.bigText")?.toString(),
            extras.getCharSequence("android.subText")?.toString()
        ).filterNotNull().filter { it.isNotBlank() }.distinct().joinToString("\n")
        if (title.isBlank() && body.isBlank()) return

        val receivedAt = System.currentTimeMillis()
        val observedAt = sbn.postTime.takeIf { it > 0L && it <= receivedAt } ?: receivedAt
        val sourceId = "android.notification:$pkg"
        val digestInput = "$pkg|\${sbn.key}|\${sbn.postTime}|$title|$body"
        val observationId = "notification:" + java.security.MessageDigest.getInstance("SHA-256")
            .digest(digestInput.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val evidence = EvidenceReference(
            evidenceId = observationId,
            evidenceType = "android_notification"
        )
        val envelope = ObservationEnvelope(
            observationId = observationId,
            source = ObservationSource(
                sourceId = sourceId,
                provider = platform,
                kind = ObservationSourceKind.NOTIFICATION,
                adapterVersion = "busybee-notification-listener/1"
            ),
            observedAtUnixMs = observedAt,
            receivedAtUnixMs = receivedAt,
            subjectId = null,
            facts = mapOf(
                "title" to ObservedFact(title.takeIf { it.isNotBlank() }, listOf(evidence)),
                "body" to ObservedFact(body.takeIf { it.isNotBlank() }, listOf(evidence)),
                "package_name" to ObservedFact(pkg, listOf(evidence))
            ),
            evidence = listOf(evidence)
        )

        val store = DriverCommandStore(applicationContext)
        // New shared observation path; legacy notice storage remains available
        // until downstream consumers have migrated to canonical BusyBee events.
        store.captureObservation(envelope)
        store.captureNotice(platform, title, body, observedAt)
    }
}
