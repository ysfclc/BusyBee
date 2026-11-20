package com.ysfclc.appobserver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCatalogTest {
    @Test
    fun selectedPackageFilterWorksForAnyAppWithoutProviderAllowlist() {
        assertTrue(AppCatalog.isSessionTarget("com.whatsapp", "com.whatsapp"))
        assertTrue(AppCatalog.isSessionTarget("com.android.settings", "com.android.settings"))
        assertTrue(AppCatalog.isSessionTarget("org.example.unknownapp", "org.example.unknownapp"))
        assertTrue(AppCatalog.isSessionTarget("com.ysfclc.appobserver.capture.generic.viewer.debug", "com.ysfclc.appobserver.capture.generic.viewer.debug"))
        assertFalse(AppCatalog.isSessionTarget("com.whatsapp", "com.instagram.android"))
        assertFalse(AppCatalog.isSessionTarget(null, "com.whatsapp"))
        assertFalse(AppCatalog.isSessionTarget("com.whatsapp", null))
    }

    @Test
    fun contentPolicyDescribesStructureOnlyPrivacy() {
        assertTrue(AppCatalog.CONTENT_POLICY.contains("explicitly selected"))
        assertTrue(AppCatalog.CONTENT_POLICY.contains("manually started session"))
        assertTrue(AppCatalog.CONTENT_POLICY.contains("typed values"))
        assertTrue(AppCatalog.CONTENT_POLICY.contains("network payloads"))
        assertTrue(AppCatalog.CONTENT_POLICY.contains("raw packet captures"))
    }
}
