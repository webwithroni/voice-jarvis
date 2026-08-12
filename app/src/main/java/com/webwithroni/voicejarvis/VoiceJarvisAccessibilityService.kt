package com.webwithroni.voicejarvis

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class VoiceJarvisAccessibilityService : AccessibilityService() {

    data class ScreenElement(
        val id: Int, val text: String, val className: String,
        val clickable: Boolean, val bounds: Rect, val node: AccessibilityNodeInfo
    )

    companion object {
        var instance: VoiceJarvisAccessibilityService? = null

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.contains(context.packageName)
        }
    }

    private var lastElements: List<ScreenElement> = emptyList()

    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onDestroy() { super.onDestroy(); instance = null }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun readScreen(): String {
        val root = rootInActiveWindow ?: return "No active window to read"
        val elements = mutableListOf<ScreenElement>()
        collect(root, elements)
        lastElements = elements
        val sb = StringBuilder("App: ${root.packageName}\n")
        elements.forEachIndexed { idx, el ->
            val kind = el.className.substringAfterLast('.')
            val tag = if (el.clickable) "clickable" else "text"
            sb.append("[$idx] $kind ($tag): \"${el.text}\"\n")
        }
        return if (elements.isEmpty()) "Screen has no readable elements" else sb.toString().take(3500)
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableList<ScreenElement>, depth: Int = 0) {
        if (depth > 30 || out.size > 80) return
        val text = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").trim()
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if ((text.isNotEmpty() || node.isClickable) && bounds.width() > 0 && bounds.height() > 0) {
            out.add(ScreenElement(out.size, text, node.className?.toString() ?: "View", node.isClickable, Rect(bounds), node))
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collect(it, out, depth + 1) }
        }
    }

    fun tapElement(id: Int): Boolean {
        val el = lastElements.getOrNull(id) ?: return false
        if (el.node.isClickable && el.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        return tapPoint(el.bounds.centerX(), el.bounds.centerY())
    }

    fun tapPoint(x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun typeText(id: Int, text: String): Boolean {
        val el = lastElements.getOrNull(id) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return el.node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun scroll(direction: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollable = findScrollable(root) ?: return false
        val action = if (direction == "up") AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        return scrollable.performAction(action)
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findScrollable(child)?.let { return it }
        }
        return null
    }

    fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun goHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
}
