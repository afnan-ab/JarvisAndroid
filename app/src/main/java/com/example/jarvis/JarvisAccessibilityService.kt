package com.example.jarvis

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Bundle

class JarvisAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun clickText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
            var p = node.parent
            while (p != null) {
                if (p.isClickable) {
                    p.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
                p = p.parent
            }
        }
        return false
    }

    fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun goHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun openRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
}
