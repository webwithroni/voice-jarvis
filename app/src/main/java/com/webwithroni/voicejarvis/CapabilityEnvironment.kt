package com.webwithroni.voicejarvis

/**
 * Runtime probe surface for the capability truth-up layer.
 *
 * Implemented by [AndroidCapabilityEnvironment] in production and by
 * fakes in unit tests, so that capability availability decisions are
 * deterministic and testable without a physical Android device.
 */
interface CapabilityEnvironment {

    /** Real runtime state of an Android permission string. */
    fun permissionState(permission: String): PermissionState

    /** Whether a required runtime/system service is currently enabled. */
    fun isServiceEnabled(service: RuntimeService): Boolean

    /** Whether the device exposes a hardware/software feature. */
    fun hasFeature(feature: String): Boolean

    /** Current device SDK level. */
    fun sdkInt(): Int
}
