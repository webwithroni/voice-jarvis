package com.webwithroni.voicejarvis

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import org.json.JSONObject

/**
 * Bridges selected Gemini tools into the Capability Bus.
 *
 * Migration strategy:
 *
 * Gemini tool
 *      ↓
 * CapabilityBusToolBridge
 *      ↓
 * CapabilityBus
 *      ↓
 * ActionPlanner
 *      ↓
 * RiskEngine
 *      ↓
 * CapabilityManager
 *      ↓
 * ActionExecutor
 *
 * Only intentionally migrated device-control tools belong here.
 *
 * All non-migrated tools continue through the legacy ToolExecutor.
 */
class CapabilityBusToolBridge(
    private val context: Context
) {

    private val bus =
        CapabilityBus(context)

    /**
     * Tools currently migrated to the Capability Bus.
     *
     * Keep this list deliberately small until every migrated
     * capability has passed CI and real-device verification.
     */
    fun handles(
        name: String
    ): Boolean {

        return when (
            name.trim()
        ) {

            "open_app",
            "read_screen",
            "scroll_screen",
            "tap_element",
            "go_back",
            "go_home",
            "get_battery",
            "toggle_flashlight",
            "set_volume",
            "set_alarm",
            "set_timer",
            "media_control",
            "call_contact",
            "send_sms" ->
                true

            else ->
                false
        }
    }

    /**
     * Execute one migrated Gemini tool.
     *
     * Gemini uses human-friendly app names.
     * The Capability Bus launch action uses package names.
     *
     * Therefore open_app resolves:
     *
     * "Instagram"
     *      ↓
     * "com.instagram.android"
     *      ↓
     * Capability Bus
     */
    fun execute(
        name: String,
        args: JSONObject
    ): ActionResult {

        return when (
            name.trim()
        ) {

            "open_app" -> {

                val appName =
                    args.optString(
                        "app_name"
                    )
                        .trim()

                if (
                    appName.isBlank()
                ) {

                    ActionResult(
                        status =
                            ActionStatus.FAILED,
                        action =
                            "open_app",
                        message =
                            "App name is required."
                    )

                } else {

                    val packageName =
                        resolvePackageName(
                            appName
                        )

                    if (
                        packageName == null
                    ) {

                        ActionResult(
                            status =
                                ActionStatus.FAILED,
                            action =
                                "open_app",
                            message =
                                "App '$appName' was not found on this device."
                        )

                    } else {

                        bus.executeSafe(
                            action = "open_app",
                            target = packageName
                        )
                    }
                }
            }

            "read_screen" -> {

                bus.executeSafe(
                    action = "read_screen"
                )
            }

            "scroll_screen" -> {

                val direction =
                    args.optString(
                        "direction",
                        "down"
                    )
                        .trim()
                        .lowercase()

                /*
                 * scroll_screen is ALWAYS vertical.
                 *
                 * Horizontal gestures belong to the separate
                 * swipe capability.
                 *
                 * Fail closed instead of silently converting a
                 * horizontal request into a vertical scroll.
                 */
                if (
                    direction != "up" &&
                    direction != "down"
                ) {

                    return ActionResult(
                        status =
                            ActionStatus.FAILED,
                        action =
                            "scroll",
                        message =
                            "scroll_screen accepts only 'up' or 'down'."
                    )
                }

                val normalizedDirection =
                    direction

                bus.executeSafe(
                    action = "scroll",
                    parameters =
                        mapOf(
                            "direction" to
                                normalizedDirection
                        )
                )
            }

            "tap_element" -> {

                val id =
                    args.optInt(
                        "id",
                        -1
                    )

                if (
                    id < 0
                ) {

                    ActionResult(
                        status =
                            ActionStatus.FAILED,
                        action =
                            "tap_element",
                        message =
                            "A valid screen element id is required."
                    )

                } else {

                    bus.executeSafe(
                        action = "tap_element",
                        parameters =
                            mapOf(
                                "id" to
                                    id.toString()
                            )
                    )
                }
            }

            "go_back" -> {

                bus.executeSafe(
                    action = "back"
                )
            }

            "go_home" -> {

                bus.executeSafe(
                    action = "home"
                )
            }

            "get_battery" -> {

                bus.executeSafe(
                    action = "get_battery"
                )
            }

            "toggle_flashlight" -> {

                val enabled =
                    args.optBoolean(
                        "on",
                        false
                    )

                bus.executeSafe(
                    action = "toggle_flashlight",
                    parameters =
                        mapOf(
                            "on" to
                                enabled.toString()
                        )
                )
            }

            "set_volume" -> {

                val percent =
                    args.optInt(
                        "percent",
                        -1
                    )

                if (
                    percent !in 0..100
                ) {

                    ActionResult(
                        status =
                            ActionStatus.FAILED,
                        action =
                            "set_volume",
                        message =
                            "Volume must be between 0 and 100 percent."
                    )

                } else {

                    bus.executeSafe(
                        action = "set_volume",
                        parameters =
                            mapOf(
                                "percent" to
                                    percent.toString()
                            )
                    )
                }
            }

            "set_alarm" -> {

                val hour =
                    args.optInt(
                        "hour",
                        -1
                    )

                val minute =
                    args.optInt(
                        "minute",
                        -1
                    )

                val label =
                    args.optString(
                        "label"
                    )

                if (
                    hour !in 0..23 ||
                    minute !in 0..59
                ) {

                    ActionResult(
                        status =
                            ActionStatus.FAILED,
                        action =
                            "set_alarm",
                        message =
                            "Alarm hour must be 0-23 and minute must be 0-59."
                    )

                } else {

                    bus.executeSafe(
                        action = "set_alarm",
                        parameters =
                            mapOf(
                                "hour" to
                                    hour.toString(),
                                "minute" to
                                    minute.toString(),
                                "label" to
                                    label
                            )
                    )
                }
            }

            "set_timer" -> {

                val seconds =
                    args.optLong(
                        "seconds",
                        -1L
                    )

                val label =
                    args.optString(
                        "label"
                    )

                if (
                    seconds <= 0L ||
                    seconds > Int.MAX_VALUE.toLong()
                ) {

                    ActionResult(
                        status =
                            ActionStatus.FAILED,
                        action =
                            "set_timer",
                        message =
                            "Timer duration must be greater than zero."
                    )

                } else {

                    bus.executeSafe(
                        action = "set_timer",
                        parameters =
                            mapOf(
                                "seconds" to
                                    seconds.toString(),
                                "label" to
                                    label
                            )
                    )
                }
            }

            "call_contact" -> {

                val nameOrNumber =
                    args.optString(
                        "name_or_number"
                    )
                        .trim()

                if (
                    nameOrNumber.isBlank()
                ) {

                    ActionResult(
                        status =
                            ActionStatus.FAILED,
                        action =
                            "call",
                        message =
                            "Contact name or phone number is required."
                    )

                } else {

                    val number =
                        resolveContactNumber(
                            nameOrNumber
                        )

                    if (
                        number == null
                    ) {

                        ActionResult(
                            status =
                                ActionStatus.FAILED,
                            action =
                                "call",
                            message =
                                "Could not find a contact matching '$nameOrNumber'."
                        )

                    } else {

                        bus.executeSafe(
                            action =
                                "call",
                            parameters =
                                mapOf(
                                    "number" to number
                                )
                        )
                    }
                }
            }

            "send_sms" -> {

                val nameOrNumber =
                    args.optString(
                        "name_or_number"
                    )
                        .trim()

                val message =
                    args.optString(
                        "message"
                    )

                if (
                    nameOrNumber.isBlank()
                ) {

                    ActionResult(
                        status =
                            ActionStatus.FAILED,
                        action =
                            "send_sms",
                        message =
                            "Contact name or phone number is required."
                    )

                } else if (
                    message.isBlank()
                ) {

                    ActionResult(
                        status =
                            ActionStatus.FAILED,
                        action =
                            "send_sms",
                        message =
                            "SMS message is required."
                    )

                } else {

                    val number =
                        resolveContactNumber(
                            nameOrNumber
                        )

                    if (
                        number == null
                    ) {

                        ActionResult(
                            status =
                                ActionStatus.FAILED,
                            action =
                                "send_sms",
                            message =
                                "Could not find a contact matching '$nameOrNumber'."
                        )

                    } else {

                        bus.executeSafe(
                            action =
                                "send_sms",
                            parameters =
                                mapOf(
                                    "number" to number,
                                    "message" to message
                                )
                        )
                    }
                }
            }

            "media_control" -> {

                val mediaAction =
                    args.optString(
                        "action"
                    )
                        .trim()
                        .lowercase()

                val allowedActions =
                    setOf(
                        "play_pause",
                        "next",
                        "previous",
                        "stop"
                    )

                if (
                    mediaAction !in allowedActions
                ) {

                    ActionResult(
                        status =
                            ActionStatus.FAILED,
                        action =
                            "media_control",
                        message =
                            "Unknown media action '$mediaAction'."
                    )

                } else {

                    bus.executeSafe(
                        action =
                            "media_control",
                        parameters =
                            mapOf(
                                "action" to
                                    mediaAction
                            )
                    )
                }
            }

            else -> {

                ActionResult(
                    status =
                        ActionStatus.UNKNOWN,
                    action =
                        name,
                    message =
                        "Tool is not migrated to the Capability Bus."
                )
            }
        }
    }

    /**
     * Resolve a contact name or raw phone number.
     *
     * Raw numbers are accepted without requiring contacts access.
     * Named contacts require READ_CONTACTS.
     */
    private fun resolveContactNumber(
        nameOrNumber: String
    ): String? {

        if (
            nameOrNumber.any { it.isDigit() } &&
            nameOrNumber.count { it.isDigit() } >= 6
        ) {
            return nameOrNumber
        }

        if (
            androidx.core.content.ContextCompat
                .checkSelfPermission(
                    context,
                    android.Manifest.permission.READ_CONTACTS
                ) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        val cursor =
            context.contentResolver.query(
                android.provider.ContactsContract
                    .CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    android.provider.ContactsContract
                        .CommonDataKinds.Phone.NUMBER,
                    android.provider.ContactsContract
                        .CommonDataKinds.Phone.DISPLAY_NAME
                ),
                "${android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf(
                    "%$nameOrNumber%"
                ),
                null
            )

        cursor?.use {

            if (
                it.moveToFirst()
            ) {

                val index =
                    it.getColumnIndex(
                        android.provider.ContactsContract
                            .CommonDataKinds.Phone.NUMBER
                    )

                if (
                    index >= 0
                ) {
                    return it.getString(
                        index
                    )
                }
            }
        }

        return null
    }

    /**
     * Resolve an installed application from a human-readable name.
     *
     * Matching order:
     *
     * 1. Exact application label
     * 2. Label starts with requested name
     * 3. Label contains requested name
     *
     * We deliberately avoid hardcoded package-name maps.
     */
    private fun resolvePackageName(
        appName: String
    ): String? {

        val packageManager =
            context.packageManager

        val launchIntent =
            Intent(
                Intent.ACTION_MAIN
            ).addCategory(
                Intent.CATEGORY_LAUNCHER
            )

        val activities =
            packageManager.queryIntentActivities(
                launchIntent,
                PackageManager.MATCH_ALL
            )

        if (
            activities.isEmpty()
        ) {
            return null
        }

        val requested =
            appName.trim()

        /*
         * Exact label match.
         */
        activities.firstOrNull { info ->

            info.loadLabel(
                packageManager
            )
                .toString()
                .trim()
                .equals(
                    requested,
                    ignoreCase = true
                )

        }?.let {

            return it.activityInfo.packageName
        }

        /*
         * Starts-with match.
         */
        activities.firstOrNull { info ->

            info.loadLabel(
                packageManager
            )
                .toString()
                .trim()
                .startsWith(
                    requested,
                    ignoreCase = true
                )

        }?.let {

            return it.activityInfo.packageName
        }

        /*
         * Contains match.
         */
        activities.firstOrNull { info ->

            info.loadLabel(
                packageManager
            )
                .toString()
                .trim()
                .contains(
                    requested,
                    ignoreCase = true
                )

        }?.let {

            return it.activityInfo.packageName
        }

        return null
    }
}
