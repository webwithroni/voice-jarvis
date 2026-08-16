package com.webwithroni.voicejarvis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM contract tests: every capability exposes complete, consistent
 * deterministic metadata, and the P0 truth-up flags are correct.
 */
class CapabilityCatalogContractTest {

    @Test
    fun allOrderedIdsAreRegistered() {
        CapabilityCatalog.orderedIds.forEach {
            assertTrue("Missing contract for '$it'", CapabilityCatalog.contracts.containsKey(it))
        }
        assertEquals(17, CapabilityCatalog.orderedIds.size)
        assertEquals(CapabilityCatalog.orderedIds.size, CapabilityCatalog.contracts.size)
    }

    @Test
    fun everyContractHasCompleteMetadata() {
        CapabilityCatalog.contracts.values.forEach { c ->
            assertTrue("id blank", c.id.isNotBlank())
            assertTrue("name blank for ${c.id}", c.name.isNotBlank())
            assertTrue("description blank for ${c.id}", c.description.isNotBlank())
            assertTrue("level negative for ${c.id}", c.level >= 0)
        }
    }

    @Test
    fun confirmationPolicyMatchesRiskEngine() {
        // Capability contract confirmation must agree with the deterministic RiskEngine.
        CapabilityCatalog.contracts.values.forEach { c ->
            assertEquals(
                "Confirmation policy mismatch for ${c.id}",
                RiskEngine.requiresConfirmation(c.risk),
                c.requiresConfirmation
            )
        }
    }

    @Test
    fun truthUpGapsAreMarkedUnimplemented() {
        assertFalse("camera must be marked not implemented",
            CapabilityCatalog.contracts[CapabilityManager.CAMERA]!!.implemented)
        assertFalse("notifications must be marked not implemented",
            CapabilityCatalog.contracts[CapabilityManager.NOTIFICATIONS]!!.implemented)
    }

    @Test
    fun previouslyMisdeclaredCapabilitiesAreImplemented() {
        // Regression guard for the latent bug where these implemented
        // capabilities fell through to an "unregistered" branch.
        listOf(CapabilityManager.FLASHLIGHT, CapabilityManager.VOLUME,
            CapabilityManager.ALARM_TIMER, CapabilityManager.DEVICE_CONTROL).forEach {
            assertTrue("$it must be implemented", CapabilityCatalog.contracts[it]!!.implemented)
            assertTrue("$it must declare an executor", CapabilityCatalog.contracts[it]!!.hasExecutor)
        }
    }

    @Test
    fun unimplementedCapabilitiesHaveNoExecutor() {
        CapabilityCatalog.contracts.values.filter { !it.implemented }.forEach {
            assertFalse("${it.id} is not implemented so must not claim an executor", it.hasExecutor)
        }
    }
}
