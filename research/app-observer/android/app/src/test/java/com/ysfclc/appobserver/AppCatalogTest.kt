package com.ysfclc.appobserver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCatalogTest {
    @Test
    fun onlyResearchTargetsAreObserved() {
        listOf("com.ubercab.driver", "ee.mtakso.driver", "com.waze",
            "com.google.android.apps.maps")
            .forEach { assertTrue(AppCatalog.isObservedPackage(it)) }
        listOf("com.whatsapp", "com.whatsapp.w4b", "com.ubercab.eats",
            "com.instagram.android", "com.android.settings", null)
            .forEach { assertFalse(AppCatalog.isObservedPackage(it)) }
    }

    @Test
    fun contentPolicyDescribesSessionScopedOptInCapture() {
        assertTrue(AppCatalog.CONTENT_POLICY.contains("selected app"))
        assertTrue(AppCatalog.CONTENT_POLICY.contains("explicit session toggle"))
        assertTrue(AppCatalog.CONTENT_POLICY.contains("password fields"))
    }
}
