package com.webwithroni.voicejarvis

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Central action execution gateway.
 *
 * New capabilities should be added here rather than directly
 * inside Gemini/JarvisService.
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

                "left" ->
                    ScreenController.Direction.LEFT

                "right" ->
                    ScreenController.Direction.RIGHT

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
                    if (result.verified) {
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
                if (result.success) {
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
                if (result.success) {
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
                if (result.success) {
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

        val node =
            service.findTextNode(
                request.parameters["field"]
                    ?: text,
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
                    if (result.success) {
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

        return ActionResult(
            status =
                if (service.goBack()) {
                    ActionStatus.EXECUTED
                } else {
                    ActionStatus.FAILED
                },
            action =
                "back",
            message =
                "Back action executed."
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

        return ActionResult(
            status =
                if (service.goHome()) {
                    ActionStatus.EXECUTED
                } else {
                    ActionStatus.FAILED
                },
            action =
                "home",
            message =
                "Home action executed."
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

        return ActionResult(
            status =
                if (service.openRecents()) {
                    ActionStatus.EXECUTED
                } else {
                    ActionStatus.FAILED
                },
            action =
                "recents",
            message =
                "Recent apps opened."
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

            if (intent == null) {

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

        } catch (e: Exception) {

            ActionResult(
                status =
                    ActionStatus.FAILED,
                action =
                    request.action,
                message =
                    "Could not launch application: ${e.message}"
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
                message
        )
    }
}
