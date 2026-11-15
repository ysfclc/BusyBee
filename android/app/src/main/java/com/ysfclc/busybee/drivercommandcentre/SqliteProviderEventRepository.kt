package com.ysfclc.busybee.drivercommandcentre

/**
 * Durable event ledger backed by the existing Driver Mirror database.
 * The repository owns no database and therefore cannot create a third store.
 */
class SqliteProviderEventRepository(
    private val mirrorStore: DriverMirrorStore
) : ProviderEventRepository {
    override fun insertIfNew(event: ProviderEvent): Boolean =
        mirrorStore.insertProviderEvent(event, ProviderEventCodec.encode(event))

    override fun events(limit: Int): List<ProviderEvent> =
        mirrorStore.providerEventBlobs(limit).map(ProviderEventCodec::decode)
}
