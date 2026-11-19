package com.ysfclc.appobserver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCatalogTest {
    @Test
    fun onlyExplicitlySelectedPackagesAreObserved() {
        listOf("com.ubercab.driver", "ee.mtakso.driver", "com.waze",
            "com.google.android.apps.maps", "com.whatsapp", "com.whatsapp.w4b")
            .forEach { assertTrue(AppCatalog.isObservedPackage(it)) }
        listOf("com.ubercab.eats", "com.instagram.android", "com.android.settings", null)
            .forEach { assertFalse(AppCatalog.isObservedPackage(it)) }
    }

    @Test
    fun contentPolicyExcludesSensitiveValues() {
        assertTrue(AppCatalog.CONTENT_POLICY.contains("No notification text"))
        assertTrue(AppCatalog.CONTENT_POLICY.contains("credentials"))
        assertTrue(AppCatalog.CONTENT_POLICY.contains("raw network payloads"))
    }
}
