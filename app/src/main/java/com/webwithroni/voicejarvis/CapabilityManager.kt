package com.webwithroni.voicejarvis

import android.content.Context
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Central capability registry.
 *
 * This is the authoritative answer to:
 *
 * "Can Jarvis perform this capability on this device?"
 *
 * Unknown capabilities are NEVER treated as available.
 */
class CapabilityManager(
    private val context: Context
) {

    companion object {

        const val UNKNOWN =
            "unknown"

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

    fun get(
        capability: String
    ): CapabilityState {

        return when (capability) {

            UNKNOWN ->
                CapabilityState(
                    id = UNKNOWN,
                    name = "Unknown Capability",
                    available = false,
                    level = 0,
                    risk = ActionRisk.HIGH,
                    description =
                        "This capability is not registered.",
                    setupRequired = false
                )

            ACCESSIBILITY ->
                CapabilityState(
                    id = ACCESSIBILITY,
                    name = "Screen Control",
                    available =
                        isAccessibilityEnabled(),
                    level =
                        if (isAccessibilityEnabled()) {
                            4
                        } else {
                            0
                        },
                    risk =
                        ActionRisk.HIGH,
                    description =
                        "Read and interact with visible apps.",
                    setupRequired =
                        !isAccessibilityEnabled()
                )

            MICROPHONE ->
                permissionCapability(
                    id = MICROPHONE,
                    name = "Microphone",
                    permission =
                        android.Manifest.permission.RECORD_AUDIO,
                    risk =
                        ActionRisk.SAFE,
                    levelWhenAvailable = 4
                )

            CONTACTS ->
                permissionCapability(
                    id = CONTACTS,
                    name = "Contacts",
                    permission =
                        android.Manifest.permission.READ_CONTACTS,
                    risk =
                        ActionRisk.MEDIUM,
                    levelWhenAvailable = 3
                )

            PHONE ->
                permissionCapability(
                    id = PHONE,
                    name = "Phone",
                    permission =
                        android.Manifest.permission.CALL_PHONE,
                    risk =
                        ActionRisk.MEDIUM,
                    levelWhenAvailable = 3
                )

            SMS ->
                permissionCapability(
                    id = SMS,
                    name = "SMS",
                    permission =
                        android.Manifest.permission.SEND_SMS,
                    risk =
                        ActionRisk.MEDIUM,
                    levelWhenAvailable = 3
                )

            LOCATION ->
                permissionCapability(
                    id = LOCATION,
                    name = "Location",
                    permission =
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                    risk =
                        ActionRisk.MEDIUM,
                    levelWhenAvailable = 3
                )

            CAMERA ->
                permissionCapability(
                    id = CAMERA,
                    name = "Camera",
                    permission =
                        android.Manifest.permission.CAMERA,
                    risk =
                        ActionRisk.MEDIUM,
                    levelWhenAvailable = 3
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
                        "Launch supported applications."
                )

            WEB_CONTROL ->
                CapabilityState(
                    id = WEB_CONTROL,
                    name = "Web Control",
                    available =
                        isAccessibilityEnabled(),
                    level =
                        if (isAccessibilityEnabled()) {
                            3
                        } else {
                            0
                        },
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
                        if (isAccessibilityEnabled()) {
                            2
                        } else {
                            0
                        },
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
                        if (isNotificationAccessGranted()) {
                            4
                        } else {
                            0
                        },
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
                        "Capability is not registered.",
                    setupRequired = false
                )
        }
    }

    fun all(): List<CapabilityState> {

        return listOf(
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
    }

    fun canExecute(
        request: ActionRequest
    ): CapabilityState {

        val capability =
            capabilityForAction(
                request.action
            )

        return get(
            capability
        )
    }

    fun capabilityForAction(
        action: String
    ): String {

        return when (
            action
                .trim()
                .lowercase()
        ) {

            "scroll",
            "swipe",
            "tap",
            "tap_element",
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

            "media_control" ->
                MEDIA

            "get_battery",
            "get_device_info" ->
                APP_CONTROL

            else ->
                UNKNOWN
        }
    }

    private fun permissionCapability(
        id: String,
        name: String,
        permission: String,
        risk: ActionRisk,
        levelWhenAvailable: Int
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
                if (available) {
                    levelWhenAvailable
                } else {
                    0
                },
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

        return ContextCompat
            .checkSelfPermission(
                context,
                permission
            ) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun isAccessibilityEnabled():
        Boolean {

        return VoiceJarvisAccessibilityService
            .isEnabled(
                context
            )
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
