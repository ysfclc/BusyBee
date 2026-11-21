package com.ysfclc.busybee.drivercommandcentre
internal object NativeBridge {
    init { System.loadLibrary("driver_core") }
    external fun engineVersion(): String
    external fun classifyOfferObservation(envelopeJson: String): String
}
