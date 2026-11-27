package com.ysfclc.busybee.drivercommandcentre

/**
 * Durable event ledger backed by the existing Driver Mirror database.
 * The repository owns no database and therefore cannot create a third store.
 * A malformed blob is reported and skipped; it is never deleted or rewritten.
 */
class SqliteProviderEventRepository(
    private val mirrorStore: DriverMirrorStore
) : ProviderEventRepository {
    @Volatile
    override var lastReadFailures: Int = 0
        private set

    override fun insertIfNew(event: ProviderEvent): Boolean =
        mirrorStore.insertProviderEvent(event, ProviderEventCodec.encode(event))

    override fun events(limit: Int): List<ProviderEvent> {
        var failures = 0
        val decoded = mutableListOf<ProviderEvent>()
        mirrorStore.providerEventBlobs(limit).forEach { encoded ->
            try {
                decoded.add(ProviderEventCodec.decode(encoded))
            } catch (_: Exception) {
                failures++
            }
        }
        lastReadFailures = failures
        return decoded
    }
}
