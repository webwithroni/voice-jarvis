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
            "go_home" ->
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

                val normalizedDirection =
                    when (
                        direction
                    ) {

                        "up",
                        "down",
                        "left",
                        "right" ->
                            direction

                        else ->
                            "down"
                    }

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
