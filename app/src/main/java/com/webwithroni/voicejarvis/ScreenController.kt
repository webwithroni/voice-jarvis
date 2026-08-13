package com.webwithroni.voicejarvis

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Central screen interaction engine.
 *
 * Design:
 *
 * 1. Prefer semantic Accessibility actions.
 * 2. Fall back to real touch gestures.
 * 3. Never keep stale AccessibilityNodeInfo references.
 * 4. Verify feed scrolling by comparing the visible screen
 *    before and after the action.
 */
class ScreenController(
    private val service: AccessibilityService
) {

    enum class Direction {
        UP,
        DOWN,
        LEFT,
        RIGHT
    }

    data class ActionResult(
        val success: Boolean,
        val verified: Boolean,
        val method: String,
        val message: String
    )

    data class ScreenElement(
        val text: String,
        val contentDescription: String,
        val className: String,
        val clickable: Boolean,
        val editable: Boolean,
        val bounds: Rect
    )

    private val handler =
        Handler(Looper.getMainLooper())

    fun scroll(
        direction: Direction,
        amount: Float = 1.0f
    ): ActionResult {

        val before =
            screenFingerprint()

        val root =
            service.rootInActiveWindow
                ?: return failure(
                    "none",
                    "No active screen is available."
                )

        val scrollNode =
            findScrollableNode(
                root,
                direction
            )

        if (scrollNode != null) {

            val action =
                accessibilityScrollAction(
                    scrollNode,
                    direction
                )

            if (
                action != null &&
                scrollNode.performAction(action)
            ) {

                scrollNode.recycle()

                return acceptedSemanticScroll(
                    before,
                    direction
                )
            }

            scrollNode.recycle()
        }

        /*
         * Social feeds frequently expose incomplete
         * accessibility scrolling. Use a real swipe.
         */
        return dispatchSwipe(
            direction,
            amount,
            before
        )
    }

    fun scrollVerified(
        direction: Direction,
        amount: Float = 1.0f,
        callback: (ActionResult) -> Unit
    ) {

        val before =
            screenFingerprint()

        val root =
            service.rootInActiveWindow

        if (root != null) {

            val scrollNode =
                findScrollableNode(
                    root,
                    direction
                )

            if (scrollNode != null) {

                val action =
                    accessibilityScrollAction(
                        scrollNode,
                        direction
                    )

                if (
                    action != null &&
                    scrollNode.performAction(action)
                ) {

                    scrollNode.recycle()

                    verifyAfterDelay(
                        before,
                        "accessibility",
                        direction,
                        callback
                    )

                    return
                }

                scrollNode.recycle()
            }
        }

        dispatchSwipeAsync(
            direction,
            amount,
            before,
            callback
        )
    }

    fun swipe(
        direction: Direction,
        amount: Float = 0.72f,
        durationMs: Long = 420L
    ): ActionResult {

        val before =
            screenFingerprint()

        return dispatchSwipe(
            direction,
            amount,
            before,
            durationMs
        )
    }

    fun tap(
        x: Float,
        y: Float
    ): ActionResult {

        if (!validPoint(x, y)) {
            return failure(
                "gesture",
                "Tap coordinates are outside the screen."
            )
        }

        val path =
            Path().apply {
                moveTo(x, y)
            }

        val gesture =
            GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(
                        path,
                        0L,
                        80L
                    )
                )
                .build()

        return dispatchGesture(
            gesture,
            "tap"
        )
    }

    fun longPress(
        x: Float,
        y: Float,
        durationMs: Long = 700L
    ): ActionResult {

        if (!validPoint(x, y)) {
            return failure(
                "gesture",
                "Long-press coordinates are outside the screen."
            )
        }

        val path =
            Path().apply {
                moveTo(x, y)
            }

        val gesture =
            GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(
                        path,
                        0L,
                        max(
                            450L,
                            durationMs
                        )
                    )
                )
                .build()

        return dispatchGesture(
            gesture,
            "long_press"
        )
    }

    fun tapNode(
        node: AccessibilityNodeInfo
    ): ActionResult {

        if (
            node.isVisibleToUser &&
            node.isClickable &&
            node.performAction(
                AccessibilityNodeInfo.ACTION_CLICK
            )
        ) {

            return ActionResult(
                true,
                true,
                "accessibility",
                "Element tapped."
            )
        }

        val bounds =
            Rect()

        node.getBoundsInScreen(bounds)

        if (
            bounds.width() <= 0 ||
            bounds.height() <= 0
        ) {

            return failure(
                "none",
                "Element has no usable screen bounds."
            )
        }

        return tap(
            bounds.centerX().toFloat(),
            bounds.centerY().toFloat()
        )
    }

    fun findText(
        text: String,
        exact: Boolean = false
    ): AccessibilityNodeInfo? {

        val root =
            service.rootInActiveWindow
                ?: return null

        return findTextRecursive(
            root,
            text.trim(),
            exact
        )
    }

    fun findClickableText(
        text: String,
        exact: Boolean = false
    ): AccessibilityNodeInfo? {

        val node =
            findText(
                text,
                exact
            )
                ?: return null

        if (node.isClickable) {
            return node
        }

        val parent =
            findClickableParent(node)

        node.recycle()

        return parent
    }

    fun readVisibleElements(
        maxElements: Int = 120
    ): List<ScreenElement> {

        val root =
            service.rootInActiveWindow
                ?: return emptyList()

        val elements =
            mutableListOf<ScreenElement>()

        collectElements(
            root,
            elements,
            maxElements
        )

        return elements
    }

    fun typeText(
        node: AccessibilityNodeInfo,
        text: String
    ): ActionResult {

        if (
            !node.isVisibleToUser
        ) {
            return failure(
                "accessibility",
                "Input element is not visible."
            )
        }

        if (
            !node.isEditable
        ) {

            return failure(
                "accessibility",
                "Element is not editable."
            )
        }

        val args =
            android.os.Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo
                        .ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }

        return if (
            node.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                args
            )
        ) {

            ActionResult(
                true,
                true,
                "accessibility",
                "Text entered."
            )

        } else {

            failure(
                "accessibility",
                "Android rejected text input."
            )
        }
    }

    fun back(): ActionResult {

        return if (
            service.performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_BACK
            )
        ) {

            ActionResult(
                true,
                true,
                "global",
                "Back action executed."
            )

        } else {

            failure(
                "global",
                "Back action failed."
            )
        }
    }

    fun home(): ActionResult {

        return if (
            service.performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_HOME
            )
        ) {

            ActionResult(
                true,
                true,
                "global",
                "Home action executed."
            )

        } else {

            failure(
                "global",
                "Home action failed."
            )
        }
    }

    fun recentApps(): ActionResult {

        return if (
            service.performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_RECENTS
            )
        ) {

            ActionResult(
                true,
                true,
                "global",
                "Recent apps opened."
            )

        } else {

            failure(
                "global",
                "Recent apps action failed."
            )
        }
    }

    private fun acceptedSemanticScroll(
        before: String,
        direction: Direction
    ): ActionResult {

        verifyAfterDelay(
            before,
            "accessibility",
            direction,
            null
        )

        return ActionResult(
            true,
            false,
            "accessibility",
            "Scroll action accepted; verification pending."
        )
    }

    private fun verifyAfterDelay(
        before: String,
        method: String,
        direction: Direction,
        callback: ((ActionResult) -> Unit)?
    ) {

        handler.postDelayed(
            {

                val after =
                    screenFingerprint()

                val changed =
                    before.isNotBlank() &&
                        after.isNotBlank() &&
                        before != after

                val result =
                    if (changed) {

                        ActionResult(
                            true,
                            true,
                            method,
                            "Scrolled ${direction.name.lowercase(Locale.US)} and verified screen change."
                        )

                    } else {

                        ActionResult(
                            false,
                            false,
                            method,
                            "Scroll was accepted but the visible screen did not change."
                        )
                    }

                callback?.invoke(result)

            },
            350L
        )
    }

    private fun dispatchSwipe(
        direction: Direction,
        amount: Float,
        before: String,
        durationMs: Long = 420L
    ): ActionResult {

        val metrics =
            service.resources
                .displayMetrics

        val width =
            metrics.widthPixels.toFloat()

        val height =
            metrics.heightPixels.toFloat()

        val centerX =
            width * 0.50f

        val centerY =
            height * 0.52f

        val safeAmount =
            amount.coerceIn(
                0.30f,
                0.90f
            )

        val horizontalDistance =
            min(
                width * 0.62f,
                width * safeAmount
            )

        val verticalDistance =
            min(
                height * 0.58f,
                height * safeAmount
            )

        val startX: Float
        val startY: Float
        val endX: Float
        val endY: Float

        when (direction) {

            Direction.UP -> {
                startX = centerX
                startY =
                    centerY +
                        verticalDistance / 2f
                endX = centerX
                endY =
                    centerY -
                        verticalDistance / 2f
            }

            Direction.DOWN -> {
                startX = centerX
                startY =
                    centerY -
                        verticalDistance / 2f
                endX = centerX
                endY =
                    centerY +
                        verticalDistance / 2f
            }

            Direction.LEFT -> {
                startX =
                    centerX +
                        horizontalDistance / 2f
                startY = centerY
                endX =
                    centerX -
                        horizontalDistance / 2f
                endY = centerY
            }

            Direction.RIGHT -> {
                startX =
                    centerX -
                        horizontalDistance / 2f
                startY = centerY
                endX =
                    centerX +
                        horizontalDistance / 2f
                endY = centerY
            }
        }

        if (
            !validPoint(
                startX,
                startY
            ) ||
            !validPoint(
                endX,
                endY
            )
        ) {

            return failure(
                "gesture",
                "Calculated swipe coordinates are invalid."
            )
        }

        val path =
            Path().apply {

                moveTo(
                    startX,
                    startY
                )

                lineTo(
                    endX,
                    endY
                )
            }

        val gesture =
            GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(
                        path,
                        0L,
                        max(
                            280L,
                            durationMs
                        )
                    )
                )
                .build()

        val result =
            dispatchGesture(
                gesture,
                "swipe_${direction.name.lowercase(Locale.US)}"
            )

        if (!result.success) {
            return result
        }

        verifyAfterDelay(
            before,
            "gesture",
            direction,
            null
        )

        return ActionResult(
            true,
            false,
            "gesture",
            "Swipe dispatched; verification pending."
        )
    }

    private fun dispatchSwipeAsync(
        direction: Direction,
        amount: Float,
        before: String,
        callback: (ActionResult) -> Unit
    ) {

        val metrics =
            service.resources
                .displayMetrics

        val width =
            metrics.widthPixels.toFloat()

        val height =
            metrics.heightPixels.toFloat()

        val centerX =
            width * 0.50f

        val centerY =
            height * 0.52f

        val safeAmount =
            amount.coerceIn(
                0.30f,
                0.90f
            )

        val horizontalDistance =
            min(
                width * 0.62f,
                width * safeAmount
            )

        val verticalDistance =
            min(
                height * 0.58f,
                height * safeAmount
            )

        val startX: Float
        val startY: Float
        val endX: Float
        val endY: Float

        when (direction) {

            Direction.UP -> {
                startX = centerX
                startY =
                    centerY +
                        verticalDistance / 2f
                endX = centerX
                endY =
                    centerY -
                        verticalDistance / 2f
            }

            Direction.DOWN -> {
                startX = centerX
                startY =
                    centerY -
                        verticalDistance / 2f
                endX = centerX
                endY =
                    centerY +
                        verticalDistance / 2f
            }

            Direction.LEFT -> {
                startX =
                    centerX +
                        horizontalDistance / 2f
                startY = centerY
                endX =
                    centerX -
                        horizontalDistance / 2f
                endY = centerY
            }

            Direction.RIGHT -> {
                startX =
                    centerX -
                        horizontalDistance / 2f
                startY = centerY
                endX =
                    centerX +
                        horizontalDistance / 2f
                endY = centerY
            }
        }

        val path =
            Path().apply {
                moveTo(
                    startX,
                    startY
                )
                lineTo(
                    endX,
                    endY
                )
            }

        val gesture =
            GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(
                        path,
                        0L,
                        420L
                    )
                )
                .build()

        val accepted =
            dispatchGesture(
                gesture,
                "swipe_${direction.name.lowercase(Locale.US)}"
            )

        if (!accepted.success) {
            callback(
                accepted
            )
            return
        }

        verifyAfterDelay(
            before,
            "gesture",
            direction,
            callback
        )
    }

    private fun dispatchGesture(
        gesture: GestureDescription,
        method: String
    ): ActionResult {

        val accepted =
            service.dispatchGesture(
                gesture,
                object :
                    AccessibilityService.GestureResultCallback() {

                    override fun onCompleted(
                        gestureDescription:
                            GestureDescription?
                    ) {
                        super.onCompleted(
                            gestureDescription
                        )
                    }

                    override fun onCancelled(
                        gestureDescription:
                            GestureDescription?
                    ) {
                        super.onCancelled(
                            gestureDescription
                        )
                    }
                },
                handler
            )

        return if (accepted) {

            ActionResult(
                true,
                false,
                method,
                "Gesture accepted by Android."
            )

        } else {

            failure(
                method,
                "Android rejected the gesture."
            )
        }
    }

    private fun findScrollableNode(
        node: AccessibilityNodeInfo,
        direction: Direction
    ): AccessibilityNodeInfo? {

        val desiredActions =
            when (direction) {

                Direction.DOWN ->
                    setOf(
                        AccessibilityNodeInfo
                            .ACTION_SCROLL_DOWN,
                        AccessibilityNodeInfo
                            .ACTION_SCROLL_FORWARD
                    )

                Direction.UP ->
                    setOf(
                        AccessibilityNodeInfo
                            .ACTION_SCROLL_UP,
                        AccessibilityNodeInfo
                            .ACTION_SCROLL_BACKWARD
                    )

                Direction.LEFT ->
                    setOf(
                        AccessibilityNodeInfo
                            .ACTION_SCROLL_LEFT,
                        AccessibilityNodeInfo
                            .ACTION_SCROLL_BACKWARD
                    )

                Direction.RIGHT ->
                    setOf(
                        AccessibilityNodeInfo
                            .ACTION_SCROLL_RIGHT,
                        AccessibilityNodeInfo
                            .ACTION_SCROLL_FORWARD
                    )
            }

        val supported =
            node.actionList.any {
                desiredActions.contains(
                    it.id
                )
            }

        if (
            supported &&
            node.isVisibleToUser
        ) {

            return AccessibilityNodeInfo.obtain(
                node
            )
        }

        for (
            i in 0 until node.childCount
        ) {

            val child =
                node.getChild(i)
                    ?: continue

            val result =
                findScrollableNode(
                    child,
                    direction
                )

            child.recycle()

            if (result != null) {
                return result
            }
        }

        return null
    }

    private fun accessibilityScrollAction(
        node: AccessibilityNodeInfo,
        direction: Direction
    ): Int? {

        val supported =
            node.actionList
                .map {
                    it.id
                }
                .toSet()

        val ordered =
            when (direction) {

                Direction.DOWN ->
                    listOf(
                        AccessibilityNodeInfo
                            .ACTION_SCROLL_DOWN,
                        AccessibilityNodeInfo
                            .ACTION_SCROLL_FORWARD
                    )

                Direction.UP ->
                    listOf(
                        AccessibilityNodeInfo
                            .ACTION_SCROLL_UP,
                        AccessibilityNodeInfo
                            .ACTION_SCROLL_BACKWARD
                    )

                Direction.LEFT ->
                    listOf(
                        AccessibilityNodeInfo
                            .ACTION_SCROLL_LEFT,
                        AccessibilityNodeInfo
                            .ACTION_SCROLL_BACKWARD
                    )

                Direction.RIGHT ->
                    listOf(
                        AccessibilityNodeInfo
                            .ACTION_SCROLL_RIGHT,
                        AccessibilityNodeInfo
                            .ACTION_SCROLL_FORWARD
                    )
            }

        return ordered.firstOrNull {
            supported.contains(it)
        }
    }

    private fun findTextRecursive(
        node: AccessibilityNodeInfo,
        text: String,
        exact: Boolean
    ): AccessibilityNodeInfo? {

        val nodeText =
            node.text
                ?.toString()
                ?.trim()
                ?: ""

        val description =
            node.contentDescription
                ?.toString()
                ?.trim()
                ?: ""

        val matches =
            if (exact) {

                nodeText.equals(
                    text,
                    ignoreCase = true
                ) ||
                    description.equals(
                        text,
                        ignoreCase = true
                    )

            } else {

                nodeText.contains(
                    text,
                    ignoreCase = true
                ) ||
                    description.contains(
                        text,
                        ignoreCase = true
                    )
            }

        if (
            matches &&
            node.isVisibleToUser
        ) {

            return AccessibilityNodeInfo.obtain(
                node
            )
        }

        for (
            i in 0 until node.childCount
        ) {

            val child =
                node.getChild(i)
                    ?: continue

            val result =
                findTextRecursive(
                    child,
                    text,
                    exact
                )

            child.recycle()

            if (result != null) {
                return result
            }
        }

        return null
    }

    private fun findClickableParent(
        node: AccessibilityNodeInfo
    ): AccessibilityNodeInfo? {

        var current =
            AccessibilityNodeInfo.obtain(
                node
            )

        repeat(6) {

            if (
                current.isClickable &&
                current.isVisibleToUser
            ) {

                return current
            }

            val parent =
                current.parent

            current.recycle()

            if (parent == null) {
                return null
            }

            current =
                AccessibilityNodeInfo.obtain(
                    parent
                )
        }

        current.recycle()
        return null
    }

    private fun collectElements(
        node: AccessibilityNodeInfo,
        output: MutableList<ScreenElement>,
        maxElements: Int,
        depth: Int = 0
    ) {

        if (
            depth > 30 ||
            output.size >= maxElements
        ) {
            return
        }

        val bounds =
            Rect()

        node.getBoundsInScreen(
            bounds
        )

        val text =
            node.text
                ?.toString()
                ?.trim()
                ?: ""

        val description =
            node.contentDescription
                ?.toString()
                ?.trim()
                ?: ""

        if (
            bounds.width() > 0 &&
            bounds.height() > 0 &&
            (
                text.isNotBlank() ||
                    description.isNotBlank() ||
                    node.isClickable ||
                    node.isEditable
            )
        ) {

            output.add(
                ScreenElement(
                    text =
                        text.take(500),

                    contentDescription =
                        description.take(500),

                    className =
                        node.className
                            ?.toString()
                            ?: "View",

                    clickable =
                        node.isClickable,

                    editable =
                        node.isEditable,

                    bounds =
                        Rect(bounds)
                )
            )
        }

        for (
            i in 0 until node.childCount
        ) {

            val child =
                node.getChild(i)
                    ?: continue

            collectElements(
                child,
                output,
                maxElements,
                depth + 1
            )

            child.recycle()

            if (
                output.size >= maxElements
            ) {
                return
            }
        }
    }

    private fun screenFingerprint(): String {

        val packageName =
            service.rootInActiveWindow
                ?.packageName
                ?.toString()
                ?: return ""

        val elements =
            readVisibleElements(
                maxElements = 80
            )

        return buildString {

            append(packageName)
            append('|')

            elements.forEach { element ->

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

                append(';')
            }
        }
            .take(14_000)
    }

    private fun validPoint(
        x: Float,
        y: Float
    ): Boolean {

        val metrics =
            service.resources
                .displayMetrics

        return x >= 0f &&
            y >= 0f &&
            x < metrics.widthPixels &&
            y < metrics.heightPixels
    }

    private fun failure(
        method: String,
        message: String
    ): ActionResult {

        return ActionResult(
            false,
            false,
            method,
            message
        )
    }
}
