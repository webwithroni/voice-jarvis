package com.webwithroni.voicejarvis

import android.content.Context
import android.provider.Settings

/**
 * Central capability registry.
 *
 * No other subsystem should guess whether a capability
 * is available. Ask this manager.
 */
class CapabilityManager(
    private val context: Context
) {

    companion object {

        const val ACCESSIBILITY =
            "screen_control"

        const val MICROPHONE =
            "microphone"

        const val NOTIFICATIONS =
            "notifications"

        const val CONTACTS =
            "contacts"

        const val PHONE =
            "phone"

        const val SMS =
            "sms"

        const val LOCATION =
            "location"

        const val FILES =
            "files"

        const val CAMERA =
            "camera"

        const val MEDIA =
            "media"

        const val APP_CONTROL =
            "app_control"

        const val WEB_CONTROL =
            "web_control"

        const val PAYMENT =
            "payment_assistance"
    }

    fun isAccessibilityEnabled(): Boolean {
        return VoiceJarvisAccessibilityService
            .isEnabled(context)
    }

    fun get(
        capability: String
    ): CapabilityState {

        return when (capability) {

            ACCESSIBILITY ->
                CapabilityState(
                    id = ACCESSIBILITY,
                    name = "Screen Control",
                    available =
                        isAccessibilityEnabled(),
                    level =
                        if (isAccessibilityEnabled()) 4 else 0,
                    risk = ActionRisk.HIGH,
                    description =
                        "Read and interact with visible apps.",
                    setupRequired =
                        !isAccessibilityEnabled()
                )

            MICROPHONE ->
                CapabilityState(
                    id = MICROPHONE,
                    name = "Microphone",
                    available = hasPermission(
                        android.Manifest.permission.RECORD_AUDIO
                    ),
                    level = 4,
                    risk = ActionRisk.SAFE,
                    description =
                        "Listen for voice commands."
                )

            CONTACTS ->
                permissionCapability(
                    CONTACTS,
                    "Contacts",
                    android.Manifest.permission.READ_CONTACTS,
                    ActionRisk.MEDIUM
                )

            PHONE ->
                permissionCapability(
                    PHONE,
                    "Phone",
                    android.Manifest.permission.CALL_PHONE,
                    ActionRisk.MEDIUM
                )

            SMS ->
                permissionCapability(
                    SMS,
                    "SMS",
                    android.Manifest.permission.SEND_SMS,
                    ActionRisk.MEDIUM
                )

            LOCATION ->
                permissionCapability(
                    LOCATION,
                    "Location",
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    ActionRisk.MEDIUM
                )

            CAMERA ->
                permissionCapability(
                    CAMERA,
                    "Camera",
                    android.Manifest.permission.CAMERA,
                    ActionRisk.MEDIUM
                )

            MEDIA ->
                CapabilityState(
                    id = MEDIA,
                    name = "Media",
                    available = true,
                    level = 4,
                    risk = ActionRisk.LOW,
                    description =
                        "Control supported media playback."
                )

            APP_CONTROL ->
                CapabilityState(
                    id = APP_CONTROL,
                    name = "App Control",
                    available = true,
                    level = 4,
                    risk = ActionRisk.LOW,
                    description =
                        "Launch and manage supported apps."
                )

            WEB_CONTROL ->
                CapabilityState(
                    id = WEB_CONTROL,
                    name = "Web Control",
                    available =
                        isAccessibilityEnabled(),
                    level =
                        if (isAccessibilityEnabled()) 3 else 0,
                    risk = ActionRisk.MEDIUM,
                    description =
                        "Interact with visible web interfaces.",
                    setupRequired =
                        !isAccessibilityEnabled()
                )

            FILES ->
                CapabilityState(
                    id = FILES,
                    name = "Files",
                    available = true,
                    level = 3,
                    risk = ActionRisk.MEDIUM,
                    description =
                        "Work with user-selected files."
                )

            PAYMENT ->
                CapabilityState(
                    id = PAYMENT,
                    name = "Payment Assistance",
                    available =
                        isAccessibilityEnabled(),
                    level =
                        if (isAccessibilityEnabled()) 2 else 0,
                    risk = ActionRisk.CRITICAL,
                    description =
                        "Prepare payment flows requiring user authorization.",
                    setupRequired =
                        !isAccessibilityEnabled()
                )

            NOTIFICATIONS ->
                CapabilityState(
                    id = NOTIFICATIONS,
                    name = "Notifications",
                    available =
                        isNotificationAccessGranted(),
                    level =
                        if (isNotificationAccessGranted()) 4 else 0,
                    risk = ActionRisk.MEDIUM,
                    description =
                        "Read and act on notifications.",
                    setupRequired =
                        !isNotificationAccessGranted()
                )

            else ->
                CapabilityState(
                    id = capability,
                    name = capability,
                    available = false,
                    level = 0,
                    risk = ActionRisk.HIGH,
                    description =
                        "Unknown capability.",
                    setupRequired = true
                )
        }
    }

    fun all(): List<CapabilityState> =
        listOf(
            get(MICROPHONE),
            get(ACCESSIBILITY),
            get(NOTIFICATIONS),
            get(CONTACTS),
            get(PHONE),
            get(SMS),
            get(LOCATION),
            get(FILES),
            get(CAMERA),
            get(MEDIA),
            get(APP_CONTROL),
            get(WEB_CONTROL),
            get(PAYMENT)
        )

    fun canExecute(
        request: ActionRequest
    ): CapabilityState {

        val capability =
            capabilityForAction(
                request.action
            )

        return get(capability)
    }

    fun capabilityForAction(
        action: String
    ): String {

        return when (
            action.lowercase()
        ) {

            "scroll",
            "swipe",
            "tap",
            "long_press",
            "type",
            "read_screen",
            "back",
            "home",
            "recents" ->
                ACCESSIBILITY

            "call" ->
                PHONE

            "send_sms",
            "send_message" ->
                SMS

            "read_notifications" ->
                NOTIFICATIONS

            "get_location" ->
                LOCATION

            "payment",
            "prepare_payment" ->
                PAYMENT

            "launch_app",
            "open_app" ->
                APP_CONTROL

            "web_open",
            "web_click",
            "web_type" ->
                WEB_CONTROL

            else ->
                APP_CONTROL
        }
    }

    private fun permissionCapability(
        id: String,
        name: String,
        permission: String,
        risk: ActionRisk
    ): CapabilityState {

        val available =
            hasPermission(
                permission
            )

        return CapabilityState(
            id = id,
            name = name,
            available = available,
            level =
                if (available) 3 else 0,
            risk = risk,
            description =
                "$name access.",
            setupRequired =
                !available
        )
    }

    private fun hasPermission(
        permission: String
    ): Boolean {

        return androidx.core.content.ContextCompat
            .checkSelfPermission(
                context,
                permission
            ) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun isNotificationAccessGranted():
        Boolean {

        return try {

            val enabled =
                Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners"
                )

            enabled?.contains(
                context.packageName
            ) == true

        } catch (_: Exception) {

            false
        }
    }
}
