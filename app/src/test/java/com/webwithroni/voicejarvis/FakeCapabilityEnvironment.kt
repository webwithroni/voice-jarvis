package com.webwithroni.voicejarvis

/**
 * Deterministic in-memory [CapabilityEnvironment] for JVM unit tests.
 * No Android dependencies.
 */
class FakeCapabilityEnvironment(
    private val permissions: MutableMap<String, PermissionState> = mutableMapOf(),
    private val services: MutableSet<RuntimeService> = mutableSetOf(),
    private val features: MutableSet<String> = mutableSetOf(),
    private var sdk: Int = 34
) : CapabilityEnvironment {

    fun grant(permission: String) = apply { permissions[permission] = PermissionState.GRANTED }
    fun deny(permission: String) = apply { permissions[permission] = PermissionState.DENIED }
    fun setPermission(permission: String, state: PermissionState) =
        apply { permissions[permission] = state }

    fun enable(service: RuntimeService) = apply { services.add(service) }
    fun disable(service: RuntimeService) = apply { services.remove(service) }

    fun withFeature(feature: String) = apply { features.add(feature) }
    fun withoutFeature(feature: String) = apply { features.remove(feature) }

    fun withSdk(value: Int) = apply { sdk = value }

    override fun permissionState(permission: String): PermissionState =
        permissions[permission] ?: PermissionState.DENIED

    override fun isServiceEnabled(service: RuntimeService): Boolean =
        services.contains(service)

    override fun hasFeature(feature: String): Boolean =
        features.contains(feature)

    override fun sdkInt(): Int = sdk
}
