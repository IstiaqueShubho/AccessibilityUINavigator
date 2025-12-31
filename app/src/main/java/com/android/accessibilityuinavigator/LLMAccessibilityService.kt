package com.android.accessibilityuinavigator

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import java.util.concurrent.ConcurrentLinkedQueue

class LLMAccessibilityService : AccessibilityService() {
    private val TAG = "LLMAccessibilityService"

    // Thread-safe queue to hold nodes for processing.
    private val nodeQueue = ConcurrentLinkedQueue<AccessibilityNodeInfo>()

    // Set to store identifiers of nodes we have already acted upon on the current screen.
    // This should only be accessed from the processingHandler to ensure thread safety.
    private val processedNodeIds = mutableSetOf<String>()

    // Handler and thread for background processing to avoid blocking.
    private lateinit var processingHandler: Handler
    private lateinit var processingThread: HandlerThread

    // Flag to prevent concurrent processing loops.
    @Volatile
    private var isProcessing = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "onServiceConnected: Accessibility Service is connected.")

        // Initialize the background thread and handler for processing.
        processingThread = HandlerThread("NodeProcessor")
        processingThread.start()
        processingHandler = Handler(processingThread.looper)

        // Configure the service.
        val info = serviceInfo
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // We must obtain a fresh copy of the root node for each event.
        val rootNode = rootInActiveWindow ?: return

        when (event.eventType) {
            // A new window state often means a new screen.
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                Log.i(TAG, "WINDOW STATE CHANGED: Clearing processed nodes and starting fresh.")
                // Post the clearing action to the handler to avoid race conditions with the processing loop.
                processingHandler.post {
                    nodeQueue.clear()
                    processedNodeIds.clear()
                }
                // Add the new root node and start processing.
                addNodeToQueueAndProcess(rootNode)
            }
            // Content changes happen within the same screen.
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // If we are not already processing, add the root and start.
                addNodeToQueueAndProcess(rootNode)
            }
            // If another event type caused a crash, you might need to handle it or recycle the node.
            else -> {
                rootNode.recycle()
            }
        }
    }

    private fun addNodeToQueueAndProcess(node: AccessibilityNodeInfo) {
        // Add the node to the queue for processing.
        // It's crucial to offer a fresh node copy that can be recycled independently.
        nodeQueue.offer(node)
        // If not already processing, start the processing loop on the handler thread.
        if (!isProcessing) {
            isProcessing = true
            processingHandler.post { processQueue() }
        } else {
            // If processing is already running, the new node is in the queue. We don't need to do anything else.
            // Avoids recycling the node here as it's now owned by the queue.
        }
    }

    /**
     * Generates a stable, unique identifier for a node based on its properties.
     */
    private fun getNodeIdentifier(node: AccessibilityNodeInfo): String {
        // Combine View ID and Content Description for a robust identifier.
        return "ID:${node.viewIdResourceName}_DESC:${node.contentDescription}"
    }

    private fun processQueue() {
        while (nodeQueue.isNotEmpty()) {
            val node = nodeQueue.poll() ?: continue

            // --- DUPLICATE ACTION PREVENTION ---
            val nodeId = getNodeIdentifier(node)
            Log.d(TAG, "processQueue: NodeInfo: $node")
            if (processedNodeIds.contains(nodeId)) {
                Log.d(TAG, "Skipping already processed node: $nodeId")
                // We still need to process its children, so don't 'continue' here,
                // but we will not perform an action on THIS node again.
            } else {
                // --- ACTION LOGIC ---
                // Only consider taking an action if the node hasn't been processed.
                if (isSearchBar(node)) {
                    Log.d(TAG, "Search bar found: $nodeId")
                    if (node.className == "android.widget.Button") {
//                        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
//                            Log.i(TAG, "Successfully clicked button: $nodeId")
//                            processedNodeIds.add(nodeId) // Mark as processed
//                        }
                    } else if (node.className == "android.widget.EditText") {
                        val success = performSearchAction(node, "Biryani") // Corrected typo
                        if (success) {
                            Log.i(TAG, "Search action successful for node: $nodeId. Halting further searches.")
                            processedNodeIds.add(nodeId) // Mark as processed
                            // Since a search was successful, you might want to stop all further actions on this screen.
                            // To do that, clear the queue.
                            nodeQueue.clear()
                            break // Exit the while loop
                        }
                    }
                }
            }

            // --- TRAVERSAL LOGIC ---
            // Add children to the queue for processing, regardless of whether we acted on the parent.
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    nodeQueue.offer(child)
                }
            }

            // Recycle the node after we are completely done with it and its children.
            node.recycle()
        }

        isProcessing = false
        Log.d(TAG, "Queue processing finished for this cycle.")
    }

    private fun performSearchAction(node: AccessibilityNodeInfo, textToSearch: String): Boolean {
        if (!node.isEditable) return false

        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToSearch)

        if (node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) {
            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                Log.d(TAG, "Successfully set text: '$textToSearch'")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)) {
                    Log.d(TAG, "Successfully performed IME Enter action.")
                    return true
                } else {
                    Log.e(TAG, "Failed to perform IME Enter action.")
                }
            } else {
                Log.e(TAG, "Failed to set text on the search bar.")
            }
        } else {
            Log.e(TAG, "Failed to focus on the search bar.")
        }
        return false
    }

    private fun isSearchBar(node: AccessibilityNodeInfo): Boolean {
        val className = node.className ?: ""
        if (className == "android.widget.EditText" || className == "android.widget.Button") {
            val viewId = node.viewIdResourceName ?: ""
            val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) node.hintText ?: "" else ""
            val contentDesc = node.contentDescription ?: ""
            val text = node.text ?: ""

            return viewId.contains("search", ignoreCase = true) ||
                    hint.contains("search", ignoreCase = true) ||
                    contentDesc.contains("search", ignoreCase = true) ||
                    text.contains("search", ignoreCase = true)
        }
        return false
    }

    override fun onInterrupt() {
        Log.w(TAG, "onInterrupt: Service interrupted.")
        if (this::processingThread.isInitialized) {
            processingThread.quitSafely()
        }
        nodeQueue.clear()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Log.i(TAG, "onUnbind: Service is being unbound.")
        onInterrupt()
        return super.onUnbind(intent)
    }
}
