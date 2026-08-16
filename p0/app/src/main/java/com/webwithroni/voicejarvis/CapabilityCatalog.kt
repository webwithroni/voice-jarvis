package com.webwithroni.voicejarvis

/**
 * Authoritative, deterministic capability contracts.
 *
 * Single source of truth for what each capability requires and whether
 * a real implementation exists. Contains no Android references, so it
 * is fully unit-testable on the JVM.
 *
 * Capability ids intentionally match the CapabilityManager constants.
 *
 * P0 truth-up notes:
 * - `camera` and `notifications` are marked implemented = false, so
 *   they resolve to NOT_SUPPORTED and can never be advertised as
 *   available (they have no working Android implementation yet).
 * - `flashlight`, `volume`, `alarm_timer`, `device_control` are
 *   declared here because they ARE implemented in ActionExecutor.
 *   The previous CapabilityManager fell through to an "unregistered"
 *   branch for these, falsely reporting them unavailable through the
 *   Capability Bus. Declaring them is the truthful state.
 */
object CapabilityCatalog {

    // Android permission strings (compile-time constants; no android.jar needed).
    const val PERM_RECORD_AUDIO = "android.permission.RECORD_AUDIO"
    const val PERM_READ_CONTACTS = "android.permission.READ_CONTACTS"
    const val PERM_CALL_PHONE = "android.permission.CALL_PHONE"
    const val PERM_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
    const val PERM_CAMERA = "android.permission.CAMERA"

    // Device feature strings.
    const val FEATURE_CAMERA_FLASH = "android.hardware.camera.flash"

    val contracts: Map<String, CapabilityContract> = listOf(

        CapabilityContract(
            id = "microphone",
            name = "Microphone",
            description = "Capture voice input for the assistant.",
            requiredPermissions = listOf(PERM_RECORD_AUDIO),
            risk = ActionRisk.SAFE,
            requiresConfirmation = false,
            requiresVerification = false,
            hasExecutor = true,
            hasVerifier = false,
            supportsRecovery = false,
            implemented = true,
            level = 4
        ),

        CapabilityContract(
            id = "screen_control",
            name = "Screen Control",
            description = "Read and interact with visible apps via accessibility.",
            requiredServices = listOf(RuntimeService.ACCESSIBILITY),
            risk = ActionRisk.HIGH,
            requiresConfirmation = true,
            requiresVerification = true,
            hasExecutor = true,
            hasVerifier = true,
            supportsRecovery = true,
            implemented = true,
            level = 4
        ),

        // TRUTH-UP GAP #2: NotificationListenerService is not declared or
        // implemented in the app, so notification access can never be
        // enabled by the user. Reported as NOT_SUPPORTED until implemented.
        CapabilityContract(
            id = "notifications",
            name = "Notifications",
            description = "Read and act on notifications (not implemented yet).",
            requiredServices = listOf(RuntimeService.NOTIFICATION_LISTENER),
            risk = ActionRisk.MEDIUM,
            requiresConfirmation = true,
            requiresVerification = false,
            hasExecutor = false,
            hasVerifier = false,
            supportsRecovery = false,
            implemented = false,
            level = 4
        ),

        CapabilityContract(
            id = "contacts",
            name = "Contacts",
            description = "Look up contacts by name.",
            requiredPermissions = listOf(PERM_READ_CONTACTS),
            risk = ActionRisk.MEDIUM,
            requiresConfirmation = true,
            requiresVerification = false,
            hasExecutor = true,
            hasVerifier = false,
            supportsRecovery = false,
            implemented = true,
            level = 3
        ),

        CapabilityContract(
            id = "phone",
            name = "Phone",
            description = "Place phone calls.",
            requiredPermissions = listOf(PERM_CALL_PHONE),
            risk = ActionRisk.MEDIUM,
            requiresConfirmation = true,
            requiresVerification = false,
            hasExecutor = true,
            hasVerifier = false,
            supportsRecovery = false,
            implemented = true,
            level = 3
        ),

        CapabilityContract(
            id = "sms",
            name = "SMS Composer",
            description = "Open the SMS composer with a prepared message. The user sends it manually.",
            risk = ActionRisk.MEDIUM,
            requiresConfirmation = true,
            requiresVerification = false,
            hasExecutor = true,
            hasVerifier = false,
            supportsRecovery = false,
            implemented = true,
            level = 3
        ),

        CapabilityContract(
            id = "location",
            name = "Location",
            description = "Read the current device location.",
            requiredPermissions = listOf(PERM_FINE_LOCATION),
            risk = ActionRisk.MEDIUM,
            requiresConfirmation = true,
            requiresVerification = false,
            hasExecutor = true,
            hasVerifier = false,
            supportsRecovery = false,
            implemented = true,
            level = 3
        ),

        CapabilityContract(
            id = "files",
            name = "Files",
            description = "Work with user-selected files.",
            risk = ActionRisk.MEDIUM,
            requiresConfirmation = true,
            requiresVerification = false,
            hasExecutor = true,
            hasVerifier = false,
            supportsRecovery = false,
            implemented = true,
            level = 3
        ),

        // TRUTH-UP GAP #1: no camera capture implementation exists and the
        // CAMERA permission is not declared in the manifest, so the user
        // cannot make this available. Reported as NOT_SUPPORTED.
        // (Torch/flashlight uses setTorchMode and is the `flashlight`
        // capability — it does NOT require the CAMERA permission.)
        CapabilityContract(
            id = "camera",
            name = "Camera",
            description = "Capture photos (not implemented yet).",
            requiredPermissions = listOf(PERM_CAMERA),
            risk = ActionRisk.MEDIUM,
            requiresConfirmation = true,
            requiresVerification = false,
            hasExecutor = false,
            hasVerifier = false,
            supportsRecovery = false,
            implemented = false,
            level = 3
        ),

        CapabilityContract(
            id = "media",
            name = "Media",
            description = "Control supported media playback.",
            risk = ActionRisk.LOW,
            requiresConfirmation = false,
            requiresVerification = false,
            hasExecutor = true,
            hasVerifier = false,
            supportsRecovery = false,
            implemented = true,
            level = 4
        ),

        CapabilityContract(
            id = "device_control",
            name = "Device Info",
            description = "Read device state such as battery.",
            risk = ActionRisk.SAFE,
            requiresConfirmation = false,
            requiresVerification = false,
            hasExecutor = true,
            hasVerifier = false,
            supportsRecovery = false,
            implemented = true,
            level = 4
        ),

        CapabilityContract(
            id = "alarm_timer",
            name = "Alarms & Timers",
            description = "Set alarms and countdown timers.",
            risk = ActionRisk.LOW,
            requiresConfirmation = false,
            requiresVerification = false,
            hasExecutor = true,
            hasVerifier = false,
            supportsRecovery = false,
            implemented = true,
            level = 3
        ),

        CapabilityContract(
            id = "flashlight",
            name = "Flashlight",
            description = "Turn the torch on or off.",
            requiredFeatures = listOf(FEATURE_CAMERA_FLASH),
            risk = ActionRisk.LOW,
            requiresConfirmation = false,
            requiresVerification = false,
            hasExecutor = true,
            hasVerifier = false,
            supportsRecovery = false,
            implemented = true,
            level = 4
        ),

        CapabilityContract(
            id = "volume",
            name = "Volume",
            description = "Set the media volume.",
            risk = ActionRisk.LOW,
            requiresConfirmation = false,
            requiresVerification = false,
            hasExecutor = true,
            hasVerifier = false,
            supportsRecovery = false,
            implemented = true,
            level = 3
        ),

        CapabilityContract(
            id = "app_control",
            name = "App Control",
            description = "Launch supported applications.",
            risk = ActionRisk.LOW,
            requiresConfirmation = false,
            requiresVerification = false,
            hasExecutor = true,
            hasVerifier = false,
            supportsRecovery = false,
            implemented = true,
            level = 4
        ),

        CapabilityContract(
            id = "web_control",
            name = "Web Control",
            description = "Interact with visible web interfaces via accessibility.",
            requiredServices = listOf(RuntimeService.ACCESSIBILITY),
            risk = ActionRisk.MEDIUM,
            requiresConfirmation = true,
            requiresVerification = true,
            hasExecutor = true,
            hasVerifier = true,
            supportsRecovery = true,
            implemented = true,
            level = 3
        ),

        CapabilityContract(
            id = "payment_assistance",
            name = "Payment Assistance",
            description = "Prepare payment flows requiring explicit user authorization.",
            requiredServices = listOf(RuntimeService.ACCESSIBILITY),
            risk = ActionRisk.CRITICAL,
            requiresConfirmation = true,
            requiresVerification = true,
            hasExecutor = true,
            hasVerifier = true,
            supportsRecovery = false,
            implemented = true,
            level = 2
        )

    ).associateBy { it.id }

    /** Ordered ids as advertised by CapabilityManager.all(). */
    val orderedIds: List<String> = listOf(
        "microphone",
        "screen_control",
        "notifications",
        "contacts",
        "phone",
        "sms",
        "location",
        "files",
        "camera",
        "media",
        "device_control",
        "alarm_timer",
        "flashlight",
        "volume",
        "app_control",
        "web_control",
        "payment_assistance"
    )
}
