package com.ysfclc.busybee.drivercommandcentre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalIntelligenceCatalogueTest {
    private val device = DeviceCapabilityProfile(
        totalRamMb = 8192,
        availableStorageMb = 20_000,
        supportedAbis = setOf("arm64-v8a"),
        hasNpu = null
    )

    private class FakeRuntime(
        override val descriptor: RuntimeDescriptor,
        private val compatible: Boolean = true
    ) : LocalInferenceRuntime {
        override fun canRun(model: ModelDescriptor, device: DeviceCapabilityProfile) = compatible
    }

    private fun model(path: String? = "/models/tiny.gguf") = ModelDescriptor(
        id = "tiny-model",
        displayName = "Tiny model",
        version = "1",
        format = ModelFormat.GGUF,
        sizeBytes = 500_000_000,
        licence = "test",
        capabilities = setOf(IntelligenceCapability.NOTIFICATION_CLASSIFICATION),
        installedPath = path
    )

    private fun runtime(availability: RuntimeAvailability = RuntimeAvailability.AVAILABLE) =
        FakeRuntime(RuntimeDescriptor("llama", "llama.cpp", setOf(ModelFormat.GGUF), availability))

    @Test
    fun selectsInstalledModelWithAvailableCompatibleRuntime() {
        val catalogue = LocalIntelligenceCatalogue()
        catalogue.registerModel(model())
        catalogue.registerRuntime(runtime())

        val target = catalogue.select(
            IntelligenceTask(IntelligenceCapability.NOTIFICATION_CLASSIFICATION, IntelligencePrivacyPolicy.LOCAL_ONLY),
            device
        )

        assertEquals(InferenceLocation.LOCAL, target.location)
        assertEquals("tiny-model", target.modelId)
        assertEquals("llama", target.runtimeId)
    }

    @Test
    fun doesNotSelectModelThatIsNotInstalled() {
        val catalogue = LocalIntelligenceCatalogue()
        catalogue.registerModel(model(path = null))
        catalogue.registerRuntime(runtime())

        val target = catalogue.select(
            IntelligenceTask(IntelligenceCapability.NOTIFICATION_CLASSIFICATION, IntelligencePrivacyPolicy.LOCAL_ONLY),
            device
        )

        assertEquals(InferenceLocation.LOCAL, target.location)
        assertNull(target.modelId)
    }

    @Test
    fun doesNotSelectUnavailableRuntime() {
        val catalogue = LocalIntelligenceCatalogue()
        catalogue.registerModel(model())
        catalogue.registerRuntime(runtime(RuntimeAvailability.NOT_INSTALLED))

        val target = catalogue.select(
            IntelligenceTask(IntelligenceCapability.NOTIFICATION_CLASSIFICATION, IntelligencePrivacyPolicy.LOCAL_ONLY),
            device
        )

        assertNull(target.runtimeId)
    }

    @Test
    fun cloudRequiresBothPermissionAndExplicitTaskOptIn() {
        val catalogue = LocalIntelligenceCatalogue()
        val denied = catalogue.select(
            IntelligenceTask(IntelligenceCapability.GENERAL_REASONING, IntelligencePrivacyPolicy.CLOUD_ALLOWED),
            device
        )
        val allowed = catalogue.select(
            IntelligenceTask(IntelligenceCapability.GENERAL_REASONING, IntelligencePrivacyPolicy.CLOUD_ALLOWED, allowCloud = true),
            device
        )

        assertEquals(InferenceLocation.LOCAL, denied.location)
        assertEquals(InferenceLocation.CLOUD, allowed.location)
    }

    @Test
    fun askBeforeCloudNeverSilentlyFallsBack() {
        val catalogue = LocalIntelligenceCatalogue()
        val target = catalogue.select(
            IntelligenceTask(IntelligenceCapability.GENERAL_REASONING, IntelligencePrivacyPolicy.ASK_BEFORE_CLOUD, allowCloud = true),
            device
        )
        assertEquals(InferenceLocation.LOCAL, target.location)
        assertTrue(target.reason.contains("approval"))
    }

    @Test
    fun registryReplacesSameModelIdWithoutDuplicatingEntries() {
        val catalogue = LocalIntelligenceCatalogue()
        catalogue.registerModel(model())
        catalogue.registerModel(model().copy(version = "2"))
        assertEquals(1, catalogue.models().size)
        assertEquals("2", catalogue.models().single().version)
    }
}
