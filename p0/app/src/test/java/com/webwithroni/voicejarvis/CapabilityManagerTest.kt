package com.webwithroni.voicejarvis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * CapabilityManager truth-up tests (Robolectric; deterministic via a
 * FakeCapabilityEnvironment, no device state involved).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CapabilityManagerTest {

    @Test
    fun cameraIsNeverAvailable() {
        val mgr = CapabilityManager(FakeCapabilityEnvironment().grant(CapabilityCatalog.PERM_CAMERA))
        assertFalse(mgr.get(CapabilityManager.CAMERA).available)
        assertEquals(CapabilityRuntimeState.NOT_SUPPORTED, mgr.availability(CapabilityManager.CAMERA).state)
    }

    @Test
    fun notificationsAreNeverAvailable() {
        val mgr = CapabilityManager(FakeCapabilityEnvironment().enable(RuntimeService.NOTIFICATION_LISTENER))
        assertFalse(mgr.get(CapabilityManager.NOTIFICATIONS).available)
        assertEquals(CapabilityRuntimeState.NOT_SUPPORTED, mgr.availability(CapabilityManager.NOTIFICATIONS).state)
    }

    @Test
    fun microphoneReflectsPermission() {
        val granted = CapabilityManager(FakeCapabilityEnvironment().grant(CapabilityCatalog.PERM_RECORD_AUDIO))
        assertTrue(granted.get(CapabilityManager.MICROPHONE).available)

        val denied = CapabilityManager(FakeCapabilityEnvironment().deny(CapabilityCatalog.PERM_RECORD_AUDIO))
        val state = denied.get(CapabilityManager.MICROPHONE)
        assertFalse(state.available)
        assertTrue("permission-gated capability should require setup", state.setupRequired)
    }

    @Test
    fun flashlightAvailableWithFlashFeature() {
        val mgr = CapabilityManager(FakeCapabilityEnvironment().withFeature(CapabilityCatalog.FEATURE_CAMERA_FLASH))
        assertTrue(mgr.get(CapabilityManager.FLASHLIGHT).available)
    }

    @Test
    fun allListsEverySeventeenCapabilities() {
        val mgr = CapabilityManager(FakeCapabilityEnvironment())
        assertEquals(17, mgr.all().size)
        assertEquals(17, mgr.allAvailability().size)
    }

    @Test
    fun unknownActionMapsToUnregisteredUnavailable() {
        val mgr = CapabilityManager(FakeCapabilityEnvironment())
        val state = mgr.canExecute(ActionRequest(action = "definitely_not_a_real_action"))
        assertEquals(CapabilityManager.UNKNOWN, state.id)
        assertFalse(state.available)
    }
}
