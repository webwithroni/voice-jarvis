package com.webwithroni.voicejarvis

/**
 * Deterministic runtime availability of a capability.
 *
 * The Capability Bus and the tool bridge must never advertise a
 * capability to the model as usable unless its state is AVAILABLE.
 */
enum class CapabilityRuntimeState {

    /** All prerequisites satisfied; the capability may be executed. */
    AVAILABLE,

    /** Registered but not usable for an unclassified reason. */
    UNAVAILABLE,

    /** A required runtime permission is missing. */
    PERMISSION_REQUIRED,

    /** A required system/runtime service is not enabled. */
    SERVICE_REQUIRED,

    /**
     * No real implementation exists in this build, or the device
     * lacks a required hardware/software feature. Can never become
     * available at runtime without a new build / different device.
     */
    NOT_SUPPORTED
}
