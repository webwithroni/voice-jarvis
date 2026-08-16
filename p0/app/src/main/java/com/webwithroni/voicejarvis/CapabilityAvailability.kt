package com.webwithroni.voicejarvis

/**
 * Deterministic availability report for a single capability.
 *
 * Produced by [CapabilityAvailabilityResolver] and queryable by the
 * Capability Bus, tool bridge and diagnostics.
 */
data class CapabilityAvailability(
    val capabilityId: String,
    val state: CapabilityRuntimeState,
    val requiredPermissions: List<String> = emptyList(),
    val missingPermissions: List<String> = emptyList(),
    val missingServices: List<RuntimeService> = emptyList(),
    val reason: String = ""
) {
    /** True only when the capability may actually be executed now. */
    val available: Boolean
        get() = state == CapabilityRuntimeState.AVAILABLE
}
