package com.webwithroni.voicejarvis

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * Central post-action verification engine.
 *
 * Important rule:
 *
 * EXECUTED != VERIFIED
 *
 * An action is only VERIFIED when we can observe evidence
 * that the requested state change actually happened.
 *
 * Verification is intentionally conservative.
 * UNKNOWN means the action may have happened, but we could
 * not prove it.
 */
class VerificationEngine(
    private val accessibilityService:
        AccessibilityService?
) {

    companion object {

        private const val MAX_ELEMENTS = 80

        private const val POLL_ATTEMPTS = 6

        private const val POLL_DELAY_MS = 150L
    }

    /**
     * Verify an action after it has already been dispatched.
     *
     * initialFingerprint should be captured immediately before
     * execution whenever screen-change verification is useful.
     */
    fun verify(
        request: ActionRequest,
        initialFingerprint: String? = null
    ): ActionResult {

        val service =
            accessibilityService
                ?: return ActionResult(
                    status = ActionStatus.UNKNOWN,
                    action = request.action,
                    message =
                        "Screen verification unavailable.",
                    verified = false
                )

        return when (request.action) {

            "open_app",
            "launch_app" -> {
                verifyAppLaunch(
                    request = request,
                    service = service
                )
            }

            "scroll",
            "swipe" -> {
                verifyScreenChange(
                    action = request.action,
                    initialFingerprint = initialFingerprint,
                    service = service
                )
            }

            "tap",
            "tap_element" -> {
                verifyScreenChange(
                    action = request.action,
                    initialFingerprint = initialFingerprint,
                    service = service
                )
            }

            "back" -> {
                verifyScreenChange(
                    action = request.action,
                    initialFingerprint = initialFingerprint,
                    service = service
                )
            }

            "home" -> {
                verifyHome(
                    service = service
                )
            }

            "recents" -> {
                verifyScreenChange(
                    action = request.action,
                    initialFingerprint = initialFingerprint,
                    service = service
                )
            }

            "type" -> {
                verifyTypedText(
                    request = request,
                    service = service
                )
            }

            else -> {
                ActionResult(
                    status = ActionStatus.EXECUTED,
                    action = request.action,
                    message =
                        "Action executed; no verification strategy is registered.",
                    verified = false
                )
            }
        }
    }

    /**
     * Verify that the requested application is actually
     * the foreground accessibility window.
     */
    private fun verifyAppLaunch(
        request: ActionRequest,
        service: AccessibilityService
    ): ActionResult {

        val expectedPackage =
            request.parameters["package"]
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: request.target
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }

        if (expectedPackage == null) {

            return ActionResult(
                status = ActionStatus.UNKNOWN,
                action = request.action,
                message =
                    "Application was launched, but no package was available for verification.",
                verified = false
            )
        }

        repeat(
            POLL_ATTEMPTS
        ) {

            val actualPackage =
                service.rootInActiveWindow
                    ?.packageName
                    ?.toString()
                    ?.trim()

            if (
                actualPackage.equals(
                    expectedPackage,
                    ignoreCase = true
                )
            ) {

                return ActionResult(
                    status = ActionStatus.VERIFIED,
                    action = request.action,
                    message =
                        "Application '$expectedPackage' is in the foreground.",
                    verified = true,
                    data =
                        mapOf(
                            "package" to expectedPackage
                        )
                )
            }

            sleepBeforeRetry()
        }

        return ActionResult(
            status = ActionStatus.UNKNOWN,
            action = request.action,
            message =
                "Application launch was dispatched, but foreground verification failed.",
            verified = false,
            data =
                mapOf(
                    "expectedPackage" to expectedPackage
                )
        )
    }

    /**
     * Verify that the visible screen changed.
     */
    private fun verifyScreenChange(
        action: String,
        initialFingerprint: String?,
        service: AccessibilityService
    ): ActionResult {

        if (initialFingerprint.isNullOrBlank()) {

            return ActionResult(
                status = ActionStatus.UNKNOWN,
                action = action,
                message =
                    "Action executed, but no initial screen fingerprint was available.",
                verified = false
            )
        }

        repeat(
            POLL_ATTEMPTS
        ) {

            val currentFingerprint =
                screenFingerprint(
                    service
                )

            if (
                currentFingerprint.isNotBlank() &&
                currentFingerprint != initialFingerprint
            ) {

                return ActionResult(
                    status = ActionStatus.VERIFIED,
                    action = action,
                    message =
                        "Screen changed after $action.",
                    verified = true
                )
            }

            sleepBeforeRetry()
        }

        return ActionResult(
            status = ActionStatus.UNKNOWN,
            action = action,
            message =
                "The $action action was dispatched, but a screen-state change could not be verified.",
            verified = false
        )
    }

    /**
     * Verify that Android actually returned to a launcher.
     */
    private fun verifyHome(
        service: AccessibilityService
    ): ActionResult {

        val homePackages =
            try {

                service.packageManager
                    .queryIntentActivities(
                        Intent(
                            Intent.ACTION_MAIN
                        ).addCategory(
                            Intent.CATEGORY_HOME
                        ),
                        0
                    )
                    .mapNotNull {
                        it.activityInfo?.packageName
                    }
                    .toSet()

            } catch (_: Exception) {

                emptySet()
            }

        if (homePackages.isEmpty()) {

            return ActionResult(
                status = ActionStatus.UNKNOWN,
                action = "home",
                message =
                    "Home action executed, but launcher package could not be determined.",
                verified = false
            )
        }

        repeat(
            POLL_ATTEMPTS
        ) {

            val currentPackage =
                service.rootInActiveWindow
                    ?.packageName
                    ?.toString()

            if (
                currentPackage != null &&
                homePackages.contains(
                    currentPackage
                )
            ) {

                return ActionResult(
                    status = ActionStatus.VERIFIED,
                    action = "home",
                    message =
                        "Android home screen is in the foreground.",
                    verified = true,
                    data =
                        mapOf(
                            "package" to currentPackage
                        )
                )
            }

            sleepBeforeRetry()
        }

        return ActionResult(
            status = ActionStatus.UNKNOWN,
            action = "home",
            message =
                "Home action was dispatched, but launcher verification failed.",
            verified = false
        )
    }

    /**
     * Verify visible typed text when accessibility exposes it.
     *
     * Password and secure fields may intentionally hide their
     * content. In those cases UNKNOWN is returned rather than
     * pretending success.
     */
    private fun verifyTypedText(
        request: ActionRequest,
        service: AccessibilityService
    ): ActionResult {

        val expectedText =
            request.parameters["text"]
                ?.takeIf {
                    it.isNotEmpty()
                }

        if (expectedText == null) {

            return ActionResult(
                status = ActionStatus.UNKNOWN,
                action = "type",
                message =
                    "Text action executed, but no expected text was supplied for verification.",
                verified = false
            )
        }

        repeat(
            POLL_ATTEMPTS
        ) {

            val root =
                service.rootInActiveWindow

            if (root != null) {

                try {

                    if (
                        containsVisibleText(
                            root,
                            expectedText
                        )
                    ) {

                        return ActionResult(
                            status = ActionStatus.VERIFIED,
                            action = "type",
                            message =
                                "Typed text is visible in the current screen.",
                            verified = true
                        )
                    }

                } finally {

                    root.recycle()
                }
            }

            sleepBeforeRetry()
        }

        return ActionResult(
            status = ActionStatus.UNKNOWN,
            action = "type",
            message =
                "Text input was dispatched, but the expected text could not be verified.",
            verified = false
        )
    }

    /**
     * Build a deterministic snapshot of the visible screen.
     *
     * AccessibilityNodeInfo references are never stored.
     */
    private fun screenFingerprint(
        service: AccessibilityService
    ): String {

        val root =
            service.rootInActiveWindow
                ?: return ""

        return try {

            buildString {

                append(
                    root.packageName
                        ?.toString()
                        .orEmpty()
                )

                append('|')

                val elements =
                    mutableListOf<SnapshotElement>()

                collectElements(
                    node = root,
                    output = elements,
                    depth = 0
                )

                elements
                    .take(
                        MAX_ELEMENTS
                    )
                    .forEach { element ->

                        append(
                            element.text
                        )

                        append('|')

                        append(
                            element.contentDescription
                        )

                        append('|')

                        append(
                            element.className
                        )

                        append('|')

                        append(
                            element.bounds.left
                        )

                        append(',')

                        append(
                            element.bounds.top
                        )

                        append(',')

                        append(
                            element.bounds.right
                        )

                        append(',')

                        append(
                            element.bounds.bottom
                        )

                        append(';')
                    }

            }
                .take(14_000)

        } finally {

            root.recycle()
        }
    }

    /**
     * Search the live accessibility tree for visible text.
     */
    private fun containsVisibleText(
        node: AccessibilityNodeInfo,
        expectedText: String
    ): Boolean {

        val nodeText =
            node.text
                ?.toString()
                .orEmpty()

        if (
            nodeText.contains(
                expectedText,
                ignoreCase = true
            )
        ) {
            return true
        }

        val description =
            node.contentDescription
                ?.toString()
                .orEmpty()

        if (
            description.contains(
                expectedText,
                ignoreCase = true
            )
        ) {
            return true
        }

        for (
            index in 0 until node.childCount
        ) {

            val child =
                node.getChild(
                    index
                )
                    ?: continue

            try {

                if (
                    containsVisibleText(
                        child,
                        expectedText
                    )
                ) {
                    return true
                }

            } finally {

                child.recycle()
            }
        }

        return false
    }

    /**
     * Convert the accessibility tree into immutable snapshot data.
     */
    private fun collectElements(
        node: AccessibilityNodeInfo,
        output: MutableList<SnapshotElement>,
        depth: Int
    ) {

        if (
            output.size >= MAX_ELEMENTS
        ) {
            return
        }

        if (
            depth > 28
        ) {
            return
        }

        val text =
            node.text
                ?.toString()
                .orEmpty()

        val description =
            node.contentDescription
                ?.toString()
                .orEmpty()

        val className =
            node.className
                ?.toString()
                .orEmpty()

        val bounds =
            Rect()

        node.getBoundsInScreen(
            bounds
        )

        if (
            text.isNotBlank() ||
            description.isNotBlank() ||
            bounds.width() > 0 ||
            bounds.height() > 0
        ) {

            output.add(
                SnapshotElement(
                    text = text,
                    contentDescription = description,
                    className = className,
                    bounds = Rect(bounds)
                )
            )
        }

        for (
            index in 0 until node.childCount
        ) {

            if (
                output.size >= MAX_ELEMENTS
            ) {
                return
            }

            val child =
                node.getChild(
                    index
                )
                    ?: continue

            try {

                collectElements(
                    node = child,
                    output = output,
                    depth = depth + 1
                )

            } finally {

                child.recycle()
            }
        }
    }

    private data class SnapshotElement(
        val text: String,
        val contentDescription: String,
        val className: String,
        val bounds: Rect
    )

    private fun sleepBeforeRetry() {

        try {

            Thread.sleep(
                POLL_DELAY_MS
            )

        } catch (
            _: InterruptedException
        ) {

            Thread.currentThread().interrupt()
        }
    }
}
