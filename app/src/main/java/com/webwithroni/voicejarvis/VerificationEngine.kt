package com.webwithroni.voicejarvis

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Post-action verification.
 *
 * Verification is deliberately conservative.
 * Unknown != success.
 */
class VerificationEngine(
    private val accessibilityService:
        AccessibilityService?
) {

    fun verify(
        request: ActionRequest,
        initialFingerprint: String? = null
    ): ActionResult {

        val action =
            request.action

        return when (action) {

            "scroll",
            "swipe" -> {

                val controller =
                    accessibilityService
                        ?.let {
                            ScreenController(it)
                        }

                if (controller == null) {

                    ActionResult(
                        status =
                            ActionStatus.UNKNOWN,
                        action = action,
                        message =
                            "Screen verification unavailable.",
                        verified = false
                    )

                } else {

                    val current =
                        readFingerprint(
                            controller
                        )

                    if (
                        initialFingerprint != null &&
                        current.isNotBlank() &&
                        current != initialFingerprint
                    ) {

                        verified(
                            action,
                            "Screen changed after $action."
                        )

                    } else {

                        ActionResult(
                            status =
                                ActionStatus.UNKNOWN,
                            action = action,
                            message =
                                "Action was dispatched but screen change could not be verified.",
                            verified = false
                        )
                    }
                }
            }

            "tap" -> {

                ActionResult(
                    status =
                        ActionStatus.EXECUTED,
                    action = action,
                    message =
                        "Tap executed; post-state verification not available for arbitrary UI elements.",
                    verified = false
                )
            }

            "type" -> {

                ActionResult(
                    status =
                        ActionStatus.EXECUTED,
                    action = action,
                    message =
                        "Text input executed.",
                    verified = false
                )
            }

            "back",
            "home",
            "recents" -> {

                ActionResult(
                    status =
                        ActionStatus.EXECUTED,
                    action = action,
                    message =
                        "${action.replaceFirstChar { it.uppercase() }} executed.",
                    verified = false
                )
            }

            else -> {

                ActionResult(
                    status =
                        ActionStatus.UNKNOWN,
                    action = action,
                    message =
                        "No verification strategy exists for this action.",
                    verified = false
                )
            }
        }
    }

    private fun readFingerprint(
        controller: ScreenController
    ): String {

        val elements =
            controller.readVisibleElements(
                80
            )

        return buildString {

            elements.forEach {
                append(
                    it.text
                )
                append('|')
                append(
                    it.contentDescription
                )
                append('|')
                append(
                    it.bounds.left
                )
                append(',')
                append(
                    it.bounds.top
                )
                append(';')
            }
        }
    }

    private fun verified(
        action: String,
        message: String
    ): ActionResult {

        return ActionResult(
            status =
                ActionStatus.VERIFIED,
            action =
                action,
            message =
                message,
            verified = true
        )
    }
}
