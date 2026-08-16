package com.webwithroni.voicejarvis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the P0 capability truth-up core.
 * A capability is only AVAILABLE when every prerequisite is satisfied.
 */
class CapabilityAvailabilityResolverTest {

    private fun resolver(env: FakeCapabilityEnvironment) = CapabilityAvailabilityResolver(env)

    @Test
    fun microphoneAvailableWhenGranted() {
        val env = FakeCapabilityEnvironment().grant(CapabilityCatalog.PERM_RECORD_AUDIO)
        val a = resolver(env).availability(CapabilityManager.MICROPHONE)
        assertEquals(CapabilityRuntimeState.AVAILABLE, a.state)
        assertTrue(a.available)
    }

    @Test
    fun microphonePermissionRequiredWhenDenied() {
        val env = FakeCapabilityEnvironment().deny(CapabilityCatalog.PERM_RECORD_AUDIO)
        val a = resolver(env).availability(CapabilityManager.MICROPHONE)
        assertEquals(CapabilityRuntimeState.PERMISSION_REQUIRED, a.state)
        assertFalse(a.available)
        assertTrue(a.missingPermissions.contains(CapabilityCatalog.PERM_RECORD_AUDIO))
    }

    @Test
    fun screenControlServiceRequiredWhenAccessibilityOff() {
        val env = FakeCapabilityEnvironment()
        val a = resolver(env).availability(CapabilityManager.ACCESSIBILITY)
        assertEquals(CapabilityRuntimeState.SERVICE_REQUIRED, a.state)
        assertTrue(a.missingServices.contains(RuntimeService.ACCESSIBILITY))
    }

    @Test
    fun screenControlAvailableWhenAccessibilityOn() {
        val env = FakeCapabilityEnvironment().enable(RuntimeService.ACCESSIBILITY)
        assertEquals(CapabilityRuntimeState.AVAILABLE, resolver(env).availability(CapabilityManager.ACCESSIBILITY).state)
    }

    // TRUTH-UP GAP #1: camera has no implementation and its permission is not
    // declared — it must NEVER report available, even if permission is granted.
    @Test
    fun cameraIsNotSupportedEvenIfPermissionGranted() {
        val env = FakeCapabilityEnvironment().grant(CapabilityCatalog.PERM_CAMERA)
        val a = resolver(env).availability(CapabilityManager.CAMERA)
        assertEquals(CapabilityRuntimeState.NOT_SUPPORTED, a.state)
        assertFalse(a.available)
    }

    // TRUTH-UP GAP #2: no NotificationListenerService exists — notifications
    // must NEVER report available, even if the listener flag were somehow set.
    @Test
    fun notificationsAreNotSupportedEvenIfServiceEnabled() {
        val env = FakeCapabilityEnvironment().enable(RuntimeService.NOTIFICATION_LISTENER)
        val a = resolver(env).availability(CapabilityManager.NOTIFICATIONS)
        assertEquals(CapabilityRuntimeState.NOT_SUPPORTED, a.state)
        assertFalse(a.available)
    }

    @Test
    fun flashlightDependsOnFlashFeature() {
        val without = FakeCapabilityEnvironment()
        assertEquals(CapabilityRuntimeState.NOT_SUPPORTED, resolver(without).availability(CapabilityManager.FLASHLIGHT).state)

        val with = FakeCapabilityEnvironment().withFeature(CapabilityCatalog.FEATURE_CAMERA_FLASH)
        assertEquals(CapabilityRuntimeState.AVAILABLE, resolver(with).availability(CapabilityManager.FLASHLIGHT).state)
    }

    @Test
    fun implementedNoPrereqCapabilitiesAreAvailable() {
        val env = FakeCapabilityEnvironment()
        listOf(CapabilityManager.SMS, CapabilityManager.MEDIA, CapabilityManager.APP_CONTROL,
            CapabilityManager.VOLUME, CapabilityManager.ALARM_TIMER, CapabilityManager.DEVICE_CONTROL, CapabilityManager.FILES)
            .forEach { assertEquals("$it should be AVAILABLE", CapabilityRuntimeState.AVAILABLE, resolver(env).availability(it).state) }
    }

    @Test
    fun paymentRequiresAccessibilityService() {
        val env = FakeCapabilityEnvironment()
        assertEquals(CapabilityRuntimeState.SERVICE_REQUIRED, resolver(env).availability(CapabilityManager.PAYMENT).state)
        assertEquals(ActionRisk.CRITICAL, CapabilityCatalog.contracts[CapabilityManager.PAYMENT]!!.risk)
    }

    @Test
    fun unknownCapabilityIsUnavailable() {
        val a = resolver(FakeCapabilityEnvironment()).availability("definitely_not_registered")
        assertEquals(CapabilityRuntimeState.UNAVAILABLE, a.state)
        assertFalse(a.available)
    }

    @Test
    fun allReturnsEveryCatalogEntry() {
        assertEquals(CapabilityCatalog.orderedIds.size, resolver(FakeCapabilityEnvironment()).all().size)
    }
}
