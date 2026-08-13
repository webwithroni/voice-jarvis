package com.webwithroni.voicejarvis

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Android accessibility bridge for Jarvis.
 *
 * Important:
 * AccessibilityNodeInfo objects are short-lived.
 * We store descriptors/bounds, never node references.
 *
 * ScreenController resolves fresh nodes whenever an action
 * needs to be executed.
 */
class VoiceJarvisAccessibilityService :
    AccessibilityService() {

    companion object {

        @Volatile
        var instance:
            VoiceJarvisAccessibilityService? = null

        fun isEnabled(
            context: Context
        ): Boolean {

            val enabled =
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                    ?: return false

            return enabled.contains(
                context.packageName
            )
        }
    }

    private lateinit var controller:
        ScreenController

    private var lastElements:
        List<ScreenController.ScreenElement> =
        emptyList()

    override fun onServiceConnected() {

        super.onServiceConnected()

        controller =
            ScreenController(
                this
            )

        instance = this
    }

    override fun onDestroy() {

        instance = null

        super.onDestroy()
    }

    override fun onAccessibilityEvent(
        event: AccessibilityEvent?
    ) {
        /*
         * Events are intentionally not processed here yet.
         *
         * Future Action/Verification engine will consume
         * only relevant window/content changes.
         */
    }

    override fun onInterrupt() {
    }

    fun readScreen(): String {

        val root =
            rootInActiveWindow
                ?: return "No active window to read."

        val collected =
            controller.readVisibleElements()

        lastElements =
            collected

        val sb =
            StringBuilder()

        sb.append(
            "App: ${root.packageName}\n"
        )

        lastElements.forEach { element ->

            val kind =
                element.className
                    .substringAfterLast('.')

            val tag =
                when {

                    element.editable ->
                        "editable"

                    element.clickable ->
                        "clickable"

                    else ->
                        "text"
                }

            val visibleText =
                when {

                    element.text.isNotBlank() ->
                        element.text

                    element.contentDescription.isNotBlank() ->
                        element.contentDescription

                    else ->
                        "(no text)"
                }

            sb.append(
                "[${element.id}] " +
                    "$kind ($tag): " +
                    "\"${visibleText.take(500)}\"\n"
            )
        }

        return if (
            lastElements.isEmpty()
        ) {

            "Screen has no readable elements."

        } else {

            sb.toString()
                .take(5000)
        }
    }

    fun tapElement(
        id: Int
    ): Boolean {

        val descriptor =
            lastElements
                .getOrNull(id)
                ?: return false

        /*
         * Resolve a fresh node from the current screen.
         */
        val node =
            resolveFreshNode(
                descriptor
            )
                ?: return controller.tap(
                    descriptor.bounds.centerX().toFloat(),
                    descriptor.bounds.centerY().toFloat()
                ).success

        return try {

            controller
                .tapNode(
                    node
                )
                .success

        } finally {

            node.recycle()
        }
    }

    fun tapPoint(
        x: Int,
        y: Int
    ): Boolean {

        return controller
            .tap(
                x.toFloat(),
                y.toFloat()
            )
            .success
    }

    fun typeText(
        id: Int,
        text: String
    ): Boolean {

        val descriptor =
            lastElements
                .getOrNull(id)
                ?: return false

        val node =
            resolveFreshNode(
                descriptor
            )
                ?: return false

        return try {

            controller
                .typeText(
                    node,
                    text
                )
                .success

        } finally {

            node.recycle()
        }
    }

    fun scroll(
        direction: String
    ): Boolean {

        val normalized =
            direction
                .trim()
                .lowercase()

        val controllerDirection =
            when (normalized) {

                "up" ->
                    ScreenController.Direction.UP

                "down" ->
                    ScreenController.Direction.DOWN

                "left" ->
                    ScreenController.Direction.LEFT

                "right" ->
                    ScreenController.Direction.RIGHT

                else ->
                    return false
            }

        return controller
            .scroll(
                controllerDirection
            )
            .success
    }

    fun swipe(
        direction: String,
        amount: Float = 0.72f
    ): Boolean {

        val controllerDirection =
            when (
                direction
                    .trim()
                    .lowercase()
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
                    return false
            }

        return controller
            .swipe(
                controllerDirection,
                amount
            )
            .success
    }

    fun tapByTextMatch(
        candidates: List<String>
    ): Boolean {

        val wanted =
            candidates
                .map {
                    it.trim()
                        .lowercase()
                }
                .filter {
                    it.isNotBlank()
                }

        if (wanted.isEmpty()) {
            return false
        }

        val elements =
            controller.readVisibleElements(
                120
            )

        /*
         * Prefer clickable elements.
         */
        val clickable =
            elements.firstOrNull { element ->

                val value =
                    (
                        element.text +
                            " " +
                            element.contentDescription
                        )
                        .lowercase()

                element.clickable &&
                    wanted.any {
                        value.contains(it)
                    }
            }

        val target =
            clickable
                ?: elements.firstOrNull { element ->

                    val value =
                        (
                            element.text +
                                " " +
                                element.contentDescription
                            )
                            .lowercase()

                    wanted.any {
                        value.contains(it)
                    }
                }
                ?: return false

        /*
         * Resolve a fresh node by semantic text.
         */
        val node =
            resolveFreshNode(
                target
            )

        if (node != null) {

            return try {

                controller
                    .tapNode(
                        node
                    )
                    .success

            } finally {

                node.recycle()
            }
        }

        return controller
            .tap(
                target.bounds.centerX().toFloat(),
                target.bounds.centerY().toFloat()
            )
            .success
    }

    fun goBack(): Boolean =
        controller
            .back()
            .success

    fun goHome(): Boolean =
        controller
            .home()
            .success

    fun openRecents(): Boolean =
        controller
            .recentApps()
            .success

    fun findTextNode(
        text: String,
        exact: Boolean = false
    ): AccessibilityNodeInfo? {

        return controller.findText(
            text,
            exact
        )
    }

    fun openAccessibilitySettings(): Boolean {

        return try {

            val intent =
                android.content.Intent(
                    Settings.ACTION_ACCESSIBILITY_SETTINGS
                ).apply {
                    flags =
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }

            startActivity(
                intent
            )

            true

        } catch (_: Exception) {

            false
        }
    }

    private fun resolveFreshNode(
        descriptor: ScreenElement
    ): AccessibilityNodeInfo? {

        val candidates =
            mutableListOf<String>()

        if (
            descriptor.text.isNotBlank()
        ) {
            candidates.add(
                descriptor.text
            )
        }

        if (
            descriptor.contentDescription.isNotBlank()
        ) {
            candidates.add(
                descriptor.contentDescription
            )
        }

        /*
         * Exact semantic lookup first.
         */
        for (
            candidate in candidates
        ) {

            val node =
                controller.findText(
                    candidate,
                    exact = true
                )

            if (node != null) {

                if (
                    sameEnough(
                        node,
                        descriptor
                    )
                ) {
                    return node
                }

                node.recycle()
            }
        }

        /*
         * Approximate semantic lookup.
         */
        for (
            candidate in candidates
        ) {

            val node =
                controller.findText(
                    candidate,
                    exact = false
                )

            if (node != null) {

                if (
                    sameEnough(
                        node,
                        descriptor,
                        tolerance = 300
                    )
                ) {
                    return node
                }

                node.recycle()
            }
        }

        return null
    }

    private fun sameEnough(
        node: AccessibilityNodeInfo,
        descriptor: ScreenElement,
        tolerance: Int = 180
    ): Boolean {

        val bounds =
            Rect()

        node.getBoundsInScreen(
            bounds
        )

        val dx =
            kotlin.math.abs(
                bounds.centerX() -
                    descriptor.bounds.centerX()
            )

        val dy =
            kotlin.math.abs(
                bounds.centerY() -
                    descriptor.bounds.centerY()
            )

        return dx <= tolerance &&
            dy <= tolerance
    }
}
