package com.example.jarvis

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent

import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: JarvisAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    fun clickText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)

        for (node in nodes) {
            if (node.isClickable &&
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ) {
                return true
            }

            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable &&
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                ) {
                    return true
                }
                parent = parent.parent
            }
        }

        return false
    }

    fun clickSendButton(): Boolean {
        val root = rootInActiveWindow ?: return false

        // Try visible "Send" text first.
        if (clickText("Send")) return true

        // Try accessibility content descriptions.
        val descriptions = listOf(
            "Send",
            "Send message",
            "send"
        )

        for (description in descriptions) {
            val nodes = root.findAccessibilityNodeInfosByText(description)
            for (node in nodes) {
                if (node.isClickable &&
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                ) {
                    return true
                }
            }
        }

        // Search the WhatsApp view tree for a node whose resource-id
        // or content description contains "send".
        return findAndClickSend(root)
    }

    private fun findAndClickSend(node: AccessibilityNodeInfo): Boolean {
        val id = node.viewIdResourceName?.lowercase() ?: ""
        val description = node.contentDescription?.toString()?.lowercase() ?: ""

        if ((id.contains("send") || description.contains("send")) &&
            node.isVisibleToUser
        ) {
            if (node.isClickable &&
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ) {
                return true
            }

            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable &&
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                ) {
                    return true
                }
                parent = parent.parent
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue

            if (findAndClickSend(child)) {
                return true
            }
        }

        return false
    }

    fun findText(text: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val nodes = root.findAccessibilityNodeInfosByText(text)

        return nodes.firstOrNull { it.isVisibleToUser }
    }

    fun findDescription(text: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return findNodeByDescription(root, text.lowercase())
    }

    private fun findNodeByDescription(
        node: AccessibilityNodeInfo,
        text: String
    ): AccessibilityNodeInfo? {
        val description =
            node.contentDescription?.toString()?.lowercase() ?: ""

        if (node.isVisibleToUser && description.contains(text)) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByDescription(child, text)
            if (result != null) return result
        }

        return null
    }

    fun setText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false

        val focused = root.findFocus(
            AccessibilityNodeInfo.FOCUS_INPUT
        )

        if (focused != null && focused.isEditable) {
            val arguments = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }

            return focused.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                arguments
            )
        }

        return false
    }

    fun scrollForward(): Boolean {
        val root = rootInActiveWindow ?: return false
        return performScroll(root, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    fun scrollBackward(): Boolean {
        val root = rootInActiveWindow ?: return false
        return performScroll(root, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    private fun performScroll(
        node: AccessibilityNodeInfo,
        action: Int
    ): Boolean {
        if (node.isVisibleToUser && node.isScrollable) {
            if (node.performAction(action)) {
                return true
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue

            if (performScroll(child, action)) {
                return true
            }
        }

        return false
    }

    fun goBack(): Boolean =
        performGlobalAction(GLOBAL_ACTION_BACK)

    fun goHome(): Boolean =
        performGlobalAction(GLOBAL_ACTION_HOME)

    fun openRecents(): Boolean =
        performGlobalAction(GLOBAL_ACTION_RECENTS)
}
