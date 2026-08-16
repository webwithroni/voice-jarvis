package com.webwithroni.voicejarvis

/**
 * Deterministic availability resolver (P0 truth-up core).
 *
 * Given a capability id, it produces a truthful [CapabilityAvailability]
 * using only the injected [CapabilityEnvironment]. It is Android-free and
 * fully unit-testable on the JVM.
 *
 * Resolution order (fail-closed):
 *  1. Unregistered id           -> UNAVAILABLE
 *  2. Not implemented in build  -> NOT_SUPPORTED
 *  3. Missing device feature    -> NOT_SUPPORTED
 *  4. Missing runtime service   -> SERVICE_REQUIRED
 *  5. Missing permission        -> PERMISSION_REQUIRED
 *  6. Otherwise                 -> AVAILABLE
 */
class CapabilityAvailabilityResolver(
    private val environment: CapabilityEnvironment,
    private val catalog: Map<String, CapabilityContract> = CapabilityCatalog.contracts
) {

    fun availability(capabilityId: String): CapabilityAvailability {

        val contract = catalog[capabilityId]
            ?: return CapabilityAvailability(
                capabilityId = capabilityId,
                state = CapabilityRuntimeState.UNAVAILABLE,
                reason = "Capability '$capabilityId' is not registered."
            )

        if (!contract.implemented) {
            return CapabilityAvailability(
                capabilityId = contract.id,
                state = CapabilityRuntimeState.NOT_SUPPORTED,
                requiredPermissions = contract.requiredPermissions,
                reason = "${contract.name} is not implemented in this build."
            )
        }

        val missingFeatures = contract.requiredFeatures.filter {
            !environment.hasFeature(it)
        }
        if (missingFeatures.isNotEmpty()) {
            return CapabilityAvailability(
                capabilityId = contract.id,
                state = CapabilityRuntimeState.NOT_SUPPORTED,
                requiredPermissions = contract.requiredPermissions,
                reason = "${contract.name} requires unavailable device feature(s): " +
                    missingFeatures.joinToString()
            )
        }

        val missingServices = contract.requiredServices.filter {
            !environment.isServiceEnabled(it)
        }
        if (missingServices.isNotEmpty()) {
            return CapabilityAvailability(
                capabilityId = contract.id,
                state = CapabilityRuntimeState.SERVICE_REQUIRED,
                requiredPermissions = contract.requiredPermissions,
                missingServices = missingServices,
                reason = "${contract.name} requires enabling: " +
                    missingServices.joinToString { it.name }
            )
        }

        val missingPermissions = contract.requiredPermissions.filter {
            environment.permissionState(it) != PermissionState.GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            return CapabilityAvailability(
                capabilityId = contract.id,
                state = CapabilityRuntimeState.PERMISSION_REQUIRED,
                requiredPermissions = contract.requiredPermissions,
                missingPermissions = missingPermissions,
                reason = "${contract.name} requires permission(s): " +
                    missingPermissions.joinToString()
            )
        }

        return CapabilityAvailability(
            capabilityId = contract.id,
            state = CapabilityRuntimeState.AVAILABLE,
            requiredPermissions = contract.requiredPermissions,
            reason = "${contract.name} is available."
        )
    }

    fun all(): List<CapabilityAvailability> =
        CapabilityCatalog.orderedIds.map { availability(it) }
}
