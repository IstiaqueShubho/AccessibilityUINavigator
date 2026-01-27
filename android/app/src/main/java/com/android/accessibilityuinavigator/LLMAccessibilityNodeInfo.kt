package com.android.accessibilityuinavigator

import android.view.accessibility.AccessibilityNodeInfo

data class LLMAccessibilityNodeInfo(
    val packageName: CharSequence?,
    val className: CharSequence?,
    val viewIdResourceName: String?,
    val contentDescription: CharSequence?,
    val text: CharSequence?,
    val hintText: CharSequence?,
    val actions: List<AccessibilityNodeInfo.AccessibilityAction>
)
