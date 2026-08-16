package com.webwithroni.voicejarvis

import android.content.Context

/**
 * Central capability registry (P0 truth-up hardened).
 *
 * This is the authoritative answer to:
 *   "Can Jarvis perform this capability on this device right now?"
 *
 * Availability decisions are delegated to the deterministic,
 * Android-free [CapabilityAvailabilityResolver], so that a capability
 * is NEVER reported available when its runtime prerequisites
 * (permissions / services / device features / implementation) are absent.
 *
 * Public API preserved for existing callers:
 *   get(), all(), canExecute(), capabilityForAction(), and the id constants.
 *
 * New in P0 (§5 availability API):
 *   availability(), availabilityForAction(), allAvailability(),
 *   permissionState().
 */
class CapabilityManager private constructor(
    private val resolver: CapabilityAvailabilityResolver,
    private val environment: CapabilityEnvironment
) {

    /** Production constructor: real Android runtime environment. */
    constructor(context: Context) : this(
        AndroidCapabilityEnvironment(context.applicationContext)
    )

    /** Environment-injected constructor (production factory + unit tests). */
    constructor(environment: CapabilityEnvironment) : this(
        CapabilityAvailabilityResolver(environment),
        environment
    )

    companion object {

        const val UNKNOWN = "unknown"
        const val ACCESSIBILITY = "screen_control"
        const val MICROPHONE = "microphone"
        const val NOTIFICATIONS = "notifications"
        const val CONTACTS = "contacts"
        const val PHONE = "phone"
        const val SMS = "sms"
        const val LOCATION = "location"
        const val FILES = "files"
        const val CAMERA = "camera"
        const val MEDIA = "media"
        const val DEVICE_CONTROL = "device_control"
        const val ALARM_TIMER = "alarm_timer"
        const val FLASHLIGHT = "flashlight"
        const val VOLUME = "volume"
        const val APP_CONTROL = "app_control"
        const val WEB_CONTROL = "web_control"
        const val PAYMENT = "payment_assistance"
    }

    // ---- New deterministic availability API (§5) ----------------------

    /** Truthful availability of a capability id. */
    fun availability(capability: String): CapabilityAvailability =
        resolver.availability(capability)

    /** Truthful availability for a raw action name. */
    fun availabilityForAction(action: String): CapabilityAvailability =
        resolver.availability(capabilityForAction(action))

    /** Availability of every registered capability, in catalog order. */
    fun allAvailability(): List<CapabilityAvailability> = resolver.all()

    /** Real runtime state of an Android permission string. */
    fun permissionState(permission: String): PermissionState =
        environment.permissionState(permission)

    // ---- Preserved public API ----------------------------------------

    fun get(capability: String): CapabilityState {

        val contract = CapabilityCatalog.contracts[capability]
        val availability = resolver.availability(capability)

        val setupRequired =
            availability.state == CapabilityRuntimeState.PERMISSION_REQUIRED ||
                availability.state == CapabilityRuntimeState.SERVICE_REQUIRED

        return CapabilityState(
            id = contract?.id ?: capability,
            name = contract?.name ?: capability,
            available = availability.available,
            level = if (availability.available) (contract?.level ?: 0) else 0,
            risk = contract?.risk ?: ActionRisk.HIGH,
            description = contract?.description ?: "Capability is not registered.",
            setupRequired = setupRequired
        )
    }

    fun all(): List<CapabilityState> =
        CapabilityCatalog.orderedIds.map { get(it) }

    fun canExecute(request: ActionRequest): CapabilityState =
        get(capabilityForAction(request.action))

    fun capabilityForAction(action: String): String {

        return when (action.trim().lowercase()) {

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

            "get_battery" ->
                DEVICE_CONTROL

            "toggle_flashlight" ->
                FLASHLIGHT

            "set_volume" ->
                VOLUME

            "set_alarm",
            "set_timer" ->
                ALARM_TIMER

            "get_device_info" ->
                APP_CONTROL

            else ->
                UNKNOWN
        }
    }
}
