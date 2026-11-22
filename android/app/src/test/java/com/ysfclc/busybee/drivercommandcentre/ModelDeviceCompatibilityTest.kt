package com.ysfclc.busybee.drivercommandcentre

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelDeviceCompatibilityTest {
    private val device = DeviceCapabilityProfile(
        totalRamMb = 8192,
        availableStorageMb = 12_000,
        supportedAbis = setOf("arm64-v8a"),
        hasNpu = null
    )

    @Test fun acceptsRequirementsMetByDevice() {
        assertTrue(ModelDeviceCompatibility.isCompatible(
            ModelRequirements(4096, 2048, setOf("arm64-v8a")), device
        ))
    }

    @Test fun rejectsInsufficientRam() {
        assertFalse(ModelDeviceCompatibility.isCompatible(ModelRequirements(minimumRamMb = 16384), device))
    }

    @Test fun rejectsInsufficientStorage() {
        assertFalse(ModelDeviceCompatibility.isCompatible(ModelRequirements(minimumFreeStorageMb = 16384), device))
    }

    @Test fun rejectsUnsupportedAbi() {
        assertFalse(ModelDeviceCompatibility.isCompatible(ModelRequirements(supportedAbis = setOf("x86_64")), device))
    }

    @Test fun unknownNpuDoesNotSatisfyNpuRequirement() {
        assertFalse(ModelDeviceCompatibility.isCompatible(ModelRequirements(requiresNpu = true), device))
    }

    @Test fun unknownRamDoesNotSatisfyDeclaredMinimum() {
        val unknown = device.copy(totalRamMb = null)
        assertFalse(ModelDeviceCompatibility.isCompatible(ModelRequirements(minimumRamMb = 1024), unknown))
    }

    @Test fun emptyRequirementsDoNotRejectUnknownHardware() {
        val unknown = device.copy(totalRamMb = null, availableStorageMb = null)
        assertTrue(ModelDeviceCompatibility.isCompatible(ModelRequirements(), unknown))
    }
}
