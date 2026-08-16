package com.webwithroni.voicejarvis

/**
 * Static, deterministic metadata describing one capability.
 *
 * This is the authoritative capability contract used by the truth-up
 * availability layer. It contains no Android references and is fully
 * unit-testable on the JVM.
 */
data class CapabilityContract(
    val id: String,
    val name: String,
    val description: String,
    val requiredPermissions: List<String> = emptyList(),
    val requiredServices: List<RuntimeService> = emptyList(),
    val requiredFeatures: List<String> = emptyList(),
    val risk: ActionRisk,
    val requiresConfirmation: Boolean,
    val requiresVerification: Boolean,
    val hasExecutor: Boolean,
    val hasVerifier: Boolean,
    val supportsRecovery: Boolean,
    /**
     * Whether a real Android implementation for this capability exists
     * in the current build. When false the capability is reported as
     * NOT_SUPPORTED and can never be advertised as available — this is
     * what prevents the model from claiming false support.
     */
    val implemented: Boolean = true,
    /** Capability level advertised when fully available (0 = none). */
    val level: Int = 3
)
