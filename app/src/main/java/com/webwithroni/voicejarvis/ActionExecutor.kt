package com.webwithroni.voicejarvis
import android.view.KeyEvent
import android.provider.AlarmClock
import android.os.BatteryManager
import android.media.AudioManager
import android.hardware.camera2.CameraManager

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Central action execution gateway.
 *
 * The Capability Bus is responsible for:
 *
 * - normalization
 * - capability checks
 * - risk validation
 * - confirmation policy
 *
 * This class is responsible only for actually performing
 * the normalized action.
 */
class ActionExecutor(
    private val context: Context
) {

    private val capabilityManager =
        CapabilityManager(context)

    private val planner =
        ActionPlanner(
            capabilityManager
        )

    private fun accessibility():
        VoiceJarvisAccessibilityService? {

        return VoiceJarvisAccessibilityService
            .instance
    }

    fun execute(
        action: String,
        target: String? = null,
        parameters: Map<String, String> = emptyMap(),
        skipConfirmation: Boolean = false
    ): ActionResult {

        val request =
            planner.plan(
                action,
                target,
                parameters
            )

        if (
            !skipConfirmation
        ) {

            val validation =
                planner.validate(
                    request
                )

            if (
                validation != null
            ) {

                return validation
            }
        }

        return when (
            request.action
        ) {

            "scroll" ->
                scroll(
                    request
                )

            "swipe" ->
                swipe(
                    request
                )

            "tap" ->
                tap(
                    request
                )

            "tap_element" ->
                tapElement(
                    request
                )

            "long_press" ->
                longPress(
                    request
                )

            "type" ->
                type(
                    request
                )

            "read_screen" ->
                readScreen(
                    request
                )

            "back" ->
                globalBack()

            "home" ->
                globalHome()

            "recents" ->
                globalRecents()

            "open_app",
            "launch_app" ->
                launchApp(
                    request
                )

            "get_battery" ->
                getBattery(
                    request
                )

            "toggle_flashlight" ->
                toggleFlashlight(
                    request
                )

            "set_volume" ->
                setVolume(
                    request
                )

            "set_alarm" ->
                setAlarm(
                    request
                )

            "set_timer" ->
                setTimer(
                    request
                )

            "media_control" ->
                mediaControl(
                    request
                )

            else ->
                ActionResult(
                    status =
                        ActionStatus.FAILED,
                    action =
                        request.action,
                    message =
                        "Unsupported action: ${request.action}"
                )
        }
    }

    private fun getBattery(
        request: ActionRequest
    ): ActionResult {

        return try {

            val batteryManager =
                context.getSystemService(
                    Context.BATTERY_SERVICE
                ) as BatteryManager

            val level =
                batteryManager.getIntProperty(
                    BatteryManager.BATTERY_PROPERTY_CAPACITY
                )

            val charging =
                batteryManager.isCharging

            ActionResult(
                status =
                    ActionStatus.EXECUTED,
                action =
                    request.action,
                message =
                    "Battery is at $level percent, ${
                        if (charging) {
                            "charging"
                        } else {
                            "not charging"
                        }
                    }.",
                verified = true,
                data =
                    mapOf(
                        "level" to
                            level.toString(),
                        "charging" to
                            charging.toString()
                    )
            )

        } catch (e: Exception) {

            ActionResult(
                status =
                    ActionStatus.FAILED,
                action =
                    request.action,
                message =
                    "Unable to read battery status: ${e.message}"
            )
        }
    }

    private fun toggleFlashlight(
        request: ActionRequest
    ): ActionResult {

        val enabled =
            request.parameters["on"]
                ?.trim()
                ?.lowercase()
                ?.let {
                    it == "true" ||
                        it == "on" ||
                        it == "1"
                }

        if (enabled == null) {

            return ActionResult(
                status =
                    ActionStatus.FAILED,
                action =
                    request.action,
                message =
                    "Flashlight state is required."
            )
        }

        return try {

            val cameraManager =
                context.getSystemService(
                    Context.CAMERA_SERVICE
                ) as CameraManager

            val cameraId =
                cameraManager.cameraIdList
                    .firstOrNull()

            if (cameraId == null) {

                ActionResult(
                    status =
                        ActionStatus.UNAVAILABLE,
                    action =
                        request.action,
                    message =
                        "No camera with flashlight was found."
                )

            } else {

                cameraManager.setTorchMode(
                    cameraId,
                    enabled
                )

                ActionResult(
                    status =
                        ActionStatus.VERIFIED,
                    action =
                        request.action,
                    message =
                        if (enabled) {
                            "Flashlight on."
                        } else {
                            "Flashlight off."
                        },
                    verified = true,
                    data =
                        mapOf(
                            "on" to
                                enabled.toString()
                        )
                )
            }

        } catch (e: SecurityException) {

            ActionResult(
                status =
                    ActionStatus.UNAVAILABLE,
                action =
                    request.action,
                message =
                    "Camera permission is required for flashlight control."
            )

        } catch (e: Exception) {

            ActionResult(
                status =
                    ActionStatus.FAILED,
                action =
                    request.action,
                message =
                    "Unable to control flashlight: ${e.message}"
            )
        }
    }

    private fun mediaControl(
        request: ActionRequest
    ): ActionResult {

        val mediaAction =
            request.parameters["action"]
                ?.trim()
                ?.lowercase()
                .orEmpty()

        val keyCode =
            when (mediaAction) {

                "play_pause" ->
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE

                "next" ->
                    KeyEvent.KEYCODE_MEDIA_NEXT

                "previous" ->
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS

                "stop" ->
                    KeyEvent.KEYCODE_MEDIA_STOP

                else ->
                    return ActionResult(
                        status =
                            ActionStatus.FAILED,
                        action =
                            request.action,
                        message =
                            "Unknown media action '$mediaAction'."
                    )
            }

        return try {

            val audioManager =
                context.getSystemService(
                    Context.AUDIO_SERVICE
                ) as AudioManager

            audioManager.dispatchMediaKeyEvent(
                KeyEvent(
                    KeyEvent.ACTION_DOWN,
                    keyCode
                )
            )

            audioManager.dispatchMediaKeyEvent(
                KeyEvent(
                    KeyEvent.ACTION_UP,
                    keyCode
                )
            )

            ActionResult(
                status =
                    ActionStatus.EXECUTED,
                action =
                    request.action,
                message =
                    "Media control executed: $mediaAction.",
                verified = false,
                data =
                    mapOf(
                        "action" to
                            mediaAction
                    )
            )

        } catch (e: Exception) {

            ActionResult(
                status =
                    ActionStatus.FAILED,
                action =
                    request.action,
                message =
                    "Unable to control media: ${e.message}"
            )
        }
    }

    private fun setVolume(
        request: ActionRequest
    ): ActionResult {

        val percent =
            request.parameters["percent"]
                ?.toIntOrNull()

        if (percent == null) {

            return ActionResult(
                status =
                    ActionStatus.FAILED,
                action =
                    request.action,
                message =
                    "Volume percentage is required."
            )
        }

        val normalized =
            percent.coerceIn(
                0,
                100
            )

        return try {

            val audioManager =
                context.getSystemService(
                    Context.AUDIO_SERVICE
                ) as AudioManager

            val max =
                audioManager.getStreamMaxVolume(
                    AudioManager.STREAM_MUSIC
                )

            val target =
                ((normalized / 100f) * max)
                    .toInt()
                    .coerceIn(
                        0,
                        max
                    )

            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                target,
                0
            )

            ActionResult(
                status =
                    ActionStatus.VERIFIED,
                action =
                    request.action,
                message =
                    "Volume set to $normalized percent.",
                verified = true,
                data =
                    mapOf(
                        "percent" to
                            normalized.toString()
                    )
            )

        } catch (e: Exception) {

            ActionResult(
                status =
                    ActionStatus.FAILED,
                action =
                    request.action,
                message =
                    "Unable to set volume: ${e.message}"
            )
        }
    }

    private fun setAlarm(
        request: ActionRequest
    ): ActionResult {

        val hour =
            request.parameters["hour"]
                ?.toIntOrNull()

        val minute =
            request.parameters["minute"]
                ?.toIntOrNull()

        val label =
            request.parameters["label"]
                ?.trim()
                .orEmpty()

        if (
            hour == null ||
            minute == null ||
            hour !in 0..23 ||
            minute !in 0..59
        ) {

            return ActionResult(
                status =
                    ActionStatus.FAILED,
                action =
                    request.action,
                message =
                    "Valid alarm hour and minute are required."
            )
        }

        return try {

            val intent =
                Intent(
                    AlarmClock.ACTION_SET_ALARM
                ).apply {

                    putExtra(
                        AlarmClock.EXTRA_HOUR,
                        hour
                    )

                    putExtra(
                        AlarmClock.EXTRA_MINUTES,
                        minute
                    )

                    if (
                        label.isNotBlank()
                    ) {

                        putExtra(
                            AlarmClock.EXTRA_MESSAGE,
                            label
                        )
                    }

                    flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK
                }

            if (
                intent.resolveActivity(
                    context.packageManager
                ) == null
            ) {

                return ActionResult(
                    status =
                        ActionStatus.UNAVAILABLE,
                    action =
                        request.action,
                    message =
                        "No alarm application is available on this device."
                )
            }

            context.startActivity(
                intent
            )

            ActionResult(
                status =
                    ActionStatus.EXECUTED,
                action =
                    request.action,
                message =
                    "Alarm request opened for ${
                        hour.toString()
                            .padStart(2, '0')
                    }:${
                        minute.toString()
                            .padStart(2, '0')
                    }.",
                verified = false,
                data =
                    mapOf(
                        "hour" to
                            hour.toString(),
                        "minute" to
                            minute.toString(),
                        "label" to
                            label
                    )
            )

        } catch (e: Exception) {

            ActionResult(
                status =
                    ActionStatus.FAILED,
                action =
                    request.action,
                message =
                    "Unable to open alarm flow: ${e.message}"
            )
        }
    }

    private fun setTimer(
        request: ActionRequest
    ): ActionResult {

        val seconds =
            request.parameters["seconds"]
                ?.toLongOrNull()

        val label =
            request.parameters["label"]
                ?.trim()
                .orEmpty()

        if (
            seconds == null ||
            seconds <= 0L
        ) {

            return ActionResult(
                status =
                    ActionStatus.FAILED,
                action =
                    request.action,
                message =
                    "Timer duration must be greater than zero."
            )
        }

        if (
            seconds > Int.MAX_VALUE.toLong()
        ) {

            return ActionResult(
                status =
                    ActionStatus.FAILED,
                action =
                    request.action,
                message =
                    "Timer duration is too large."
            )
        }

        return try {

            val intent =
                Intent(
                    AlarmClock.ACTION_SET_TIMER
                ).apply {

                    putExtra(
                        AlarmClock.EXTRA_LENGTH,
                        seconds.toInt()
                    )

                    if (
                        label.isNotBlank()
                    ) {

                        putExtra(
                            AlarmClock.EXTRA_MESSAGE,
                            label
                        )
                    }

                    /*
                     * Do not force skip-UI.
                     *
                     * OEM alarm applications may handle the
                     * ACTION_SET_TIMER contract differently.
                     */
                    flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK
                }

            if (
                intent.resolveActivity(
                    context.packageManager
                ) == null
            ) {

                return ActionResult(
                    status =
                        ActionStatus.UNAVAILABLE,
                    action =
                        request.action,
                    message =
                        "No timer application is available on this device."
                )
            }

            context.startActivity(
                intent
            )

            ActionResult(
                status =
                    ActionStatus.EXECUTED,
                action =
                    request.action,
                message =
                    "Timer request opened for $seconds seconds.",
                verified = false,
                data =
                    mapOf(
                        "seconds" to
                            seconds.toString(),
                        "label" to
                            label
                    )
            )

        } catch (e: Exception) {

            ActionResult(
                status =
                    ActionStatus.FAILED,
                action =
                    request.action,
                message =
                    "Unable to open timer flow: ${e.message}"
            )
        }
    }

    private fun scroll(
        request: ActionRequest
    ): ActionResult {

        val service =
            accessibility()
                ?: return unavailable(
                    request.action,
                    "Accessibility service is not enabled."
                )

        val direction =
            when (
                request.parameters["direction"]
                    ?.lowercase()
            ) {

                "up" ->
                    ScreenController.Direction.UP

                "down" ->
                    ScreenController.Direction.DOWN

                /*
                 * scroll is strictly vertical.
                 *
                 * Horizontal movement belongs to swipe().
                 */
                else ->
                    ScreenController.Direction.DOWN
            }

        val result =
            ScreenController(
                service
            ).scroll(
                direction,
                request.parameters["amount"]
                    ?.toFloatOrNull()
                    ?: 0.72f
            )

        return if (
            result.success
        ) {

            ActionResult(
                status =
                    if (
                        result.verified
                    ) {
                        ActionStatus.VERIFIED
                    } else {
                        ActionStatus.EXECUTED
                    },
                action =
                    request.action,
                message =
                    result.message,
                verified =
                    result.verified
            )

        } else {

            ActionResult(
                status =
                    ActionStatus.FAILED,
                action =
                    request.action,
                message =
                    result.message
            )
        }
    }

    private fun swipe(
        request: ActionRequest
    ): ActionResult {

        val service =
            accessibility()
                ?: return unavailable(
                    request.action,
                    "Accessibility service is not enabled."
                )

        val direction =
            when (
                request.parameters["direction"]
                    ?.lowercase()
            ) {

                "up" ->
                    ScreenController.Direction.UP

                "down" ->
                    ScreenController.Direction.DOWN

                "left" ->
                    ScreenController.Direction.LEFT

                "right" ->
                    ScreenController.Direction.RIGHT

                else ->
                    ScreenController.Direction.UP
            }

        val result =
            ScreenController(
                service
            ).swipe(
                direction,
                request.parameters["amount"]
                    ?.toFloatOrNull()
                    ?: 0.72f
            )

        return ActionResult(
            status =
                if (
                    result.success
                ) {
                    if (
                        result.verified
                    ) {
                        ActionStatus.VERIFIED
                    } else {
                        ActionStatus.EXECUTED
                    }
                } else {
                    ActionStatus.FAILED
                },
            action =
                request.action,
            message =
                result.message,
            verified =
                result.verified
        )
    }

    private fun tap(
        request: ActionRequest
    ): ActionResult {

        val service =
            accessibility()
                ?: return unavailable(
                    request.action,
                    "Accessibility service is not enabled."
                )

        val x =
            request.parameters["x"]
                ?.toFloatOrNull()

        val y =
            request.parameters["y"]
                ?.toFloatOrNull()

        if (
            x == null ||
            y == null
        ) {

            return ActionResult(
                status =
                    ActionStatus.FAILED,
                action =
                    request.action,
                message =
                    "Tap coordinates are required."
            )
        }

        val result =
            ScreenController(
                service
            ).tap(
                x,
                y
            )

        return ActionResult(
            status =
                if (
                    result.success
                ) {
                    ActionStatus.EXECUTED
                } else {
                    ActionStatus.FAILED
                },
            action =
                request.action,
            message =
                result.message,
            verified =
                result.verified
        )
    }

    /**
     * Tap an element from the latest accessibility snapshot.
     *
     * VoiceJarvisAccessibilityService internally resolves the
     * descriptor to a fresh AccessibilityNodeInfo before tapping.
     */
    private fun tapElement(
        request: ActionRequest
    ): ActionResult {

        val service =
            accessibility()
                ?: return unavailable(
                    request.action,
                    "Accessibility service is not enabled."
                )

        val id =
            request.parameters["id"]
                ?.toIntOrNull()

        if (
            id == null ||
            id < 0
        ) {

            return ActionResult(
                status =
                    ActionStatus.FAILED,
                action =
                    request.action,
                message =
                    "A valid screen element id is required."
            )
        }

        val success =
            service.tapElement(
                id
            )

        return ActionResult(
            status =
                if (
                    success
                ) {
                    ActionStatus.EXECUTED
                } else {
                    ActionStatus.FAILED
                },
            action =
                request.action,
            message =
                if (
                    success
                ) {
                    "Screen element $id tapped."
                } else {
                    "Unable to tap screen element $id."
                },
            verified = false
        )
    }

    private fun longPress(
        request: ActionRequest
    ): ActionResult {

        val service =
            accessibility()
                ?: return unavailable(
                    request.action,
                    "Accessibility service is not enabled."
                )

        val x =
            request.parameters["x"]
                ?.toFloatOrNull()

        val y =
            request.parameters["y"]
                ?.toFloatOrNull()

        if (
            x == null ||
            y == null
        ) {

            return ActionResult(
                status =
                    ActionStatus.FAILED,
                action =
                    request.action,
                message =
                    "Long-press coordinates are required."
            )
        }

        val duration =
            request.parameters["durationMs"]
                ?.toLongOrNull()
                ?: 700L

        val result =
            ScreenController(
                service
            ).longPress(
                x,
                y,
                duration
            )

        return ActionResult(
            status =
                if (
                    result.success
                ) {
                    ActionStatus.EXECUTED
                } else {
                    ActionStatus.FAILED
                },
            action =
                request.action,
            message =
                result.message,
            verified =
                result.verified
        )
    }

    private fun type(
        request: ActionRequest
    ): ActionResult {

        val service =
            accessibility()
                ?: return unavailable(
                    request.action,
                    "Accessibility service is not enabled."
                )

        val text =
            request.parameters["text"]
                ?: return ActionResult(
                    status =
                        ActionStatus.FAILED,
                    action =
                        request.action,
                    message =
                        "Text is required."
                )

        /*
         * Gemini's type_text tool provides a numeric element id
         * from the most recent read_screen call.
         *
         * Prefer that authoritative descriptor path.
         */
        val id =
            request.parameters["id"]
                ?.toIntOrNull()

        if (
            id != null
        ) {

            if (
                id < 0
            ) {

                return ActionResult(
                    status =
                        ActionStatus.FAILED,
                    action =
                        request.action,
                    message =
                        "A valid screen element id is required."
                )
            }

            val success =
                service.typeText(
                    id,
                    text
                )

            return ActionResult(
                status =
                    if (success) {
                        ActionStatus.EXECUTED
                    } else {
                        ActionStatus.FAILED
                    },
                action =
                    request.action,
                message =
                    if (success) {
                        "Text entered into screen element $id."
                    } else {
                        "Could not type into screen element $id."
                    },
                verified =
                    false
            )
        }

        /*
         * Preserve the existing field-based path for internal
         * callers that do not use a numeric element id.
         */
        val field =
            request.parameters["field"]
                ?: return ActionResult(
                    status =
                        ActionStatus.FAILED,
                    action =
                        request.action,
                    message =
                        "Input element id or field is required."
                )

        val node =
            service.findTextNode(
                field,
                exact = false
            )

        if (
            node == null
        ) {

            return ActionResult(
                status =
                    ActionStatus.FAILED,
                action =
                    request.action,
                message =
                    "Input field could not be found."
            )
        }

        return try {

            val result =
                ScreenController(
                    service
                ).typeText(
                    node,
                    text
                )

            ActionResult(
                status =
                    if (
                        result.success
                    ) {
                        ActionStatus.EXECUTED
                    } else {
                        ActionStatus.FAILED
                    },
                action =
                    request.action,
                message =
                    result.message,
                verified =
                    result.verified
            )

        } finally {

            node.recycle()
        }
    }

    private fun readScreen(
        request: ActionRequest
    ): ActionResult {

        val service =
            accessibility()
                ?: return unavailable(
                    request.action,
                    "Accessibility service is not enabled."
                )

        val content =
            service.readScreen()

        return ActionResult(
            status =
                ActionStatus.VERIFIED,
            action =
                request.action,
            message =
                "Screen read successfully.",
            verified = true,
            data =
                mapOf(
                    "screen" to content
                )
        )
    }

    private fun globalBack():
        ActionResult {

        val service =
            accessibility()
                ?: return unavailable(
                    "back",
                    "Accessibility service is not enabled."
                )

        val success =
            service.goBack()

        return ActionResult(
            status =
                if (
                    success
                ) {
                    ActionStatus.EXECUTED
                } else {
                    ActionStatus.FAILED
                },
            action =
                "back",
            message =
                if (
                    success
                ) {
                    "Back action executed."
                } else {
                    "Back action failed."
                }
        )
    }

    private fun globalHome():
        ActionResult {

        val service =
            accessibility()
                ?: return unavailable(
                    "home",
                    "Accessibility service is not enabled."
                )

        val success =
            service.goHome()

        return ActionResult(
            status =
                if (
                    success
                ) {
                    ActionStatus.EXECUTED
                } else {
                    ActionStatus.FAILED
                },
            action =
                "home",
            message =
                if (
                    success
                ) {
                    "Home action executed."
                } else {
                    "Home action failed."
                }
        )
    }

    private fun globalRecents():
        ActionResult {

        val service =
            accessibility()
                ?: return unavailable(
                    "recents",
                    "Accessibility service is not enabled."
                )

        val success =
            service.openRecents()

        return ActionResult(
            status =
                if (
                    success
                ) {
                    ActionStatus.EXECUTED
                } else {
                    ActionStatus.FAILED
                },
            action =
                "recents",
            message =
                if (
                    success
                ) {
                    "Recent apps opened."
                } else {
                    "Unable to open recent apps."
                }
        )
    }

    private fun launchApp(
        request: ActionRequest
    ): ActionResult {

        val packageName =
            request.parameters["package"]
                ?: request.target

        if (
            packageName.isNullOrBlank()
        ) {

            return ActionResult(
                status =
                    ActionStatus.FAILED,
                action =
                    request.action,
                message =
                    "Application package is required."
            )
        }

        return try {

            val intent =
                context.packageManager
                    .getLaunchIntentForPackage(
                        packageName
                    )

            if (
                intent == null
            ) {

                ActionResult(
                    status =
                        ActionStatus.FAILED,
                    action =
                        request.action,
                    message =
                        "Application is not installed."
                )

            } else {

                intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )

                context.startActivity(
                    intent
                )

                ActionResult(
                    status =
                        ActionStatus.EXECUTED,
                    action =
                        request.action,
                    message =
                        "Application launched."
                )
            }

        } catch (
            e: Exception
        ) {

            ActionResult(
                status =
                    ActionStatus.FAILED,
                action =
                    request.action,
                message =
                    "Application launch failed: " +
                        (
                            e.message
                                ?: e.javaClass.simpleName
                        )
            )
        }
    }

    private fun unavailable(
        action: String,
        message: String
    ): ActionResult {

        return ActionResult(
            status =
                ActionStatus.UNAVAILABLE,
            action =
                action,
            message =
                message,
            verified = false
        )
    }
}
