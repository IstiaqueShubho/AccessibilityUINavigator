package com.android.accessibilityuinavigator

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.ui.unit.Constraints
import androidx.core.content.ContextCompat.startActivity

object LLMHelper {
    private const val TAG = "LLMHelper"
    private const val SERVER_URL = "http://192.168.1.101:8000"

    suspend fun callLLM(task: String): Map<String, Any>? {
        Log.i(TAG, "callLLM: $task")
        val client = LLMImplementation(SERVER_URL)
        val isHealthy = client.checkHealth()
        if (!isHealthy) {
            Log.e(TAG, "Server not reachable")
            return mapOf("error" to "Server not reachable")
        }

        // Make a synchronous call - waits for result
        val response = client.invokeSync(task)
        if (response != null) {
            Log.i(TAG, "Result: ${response.result}")
        }
        Log.i(TAG, "callLLM: $response")
        return response?.result

    }

    fun openSetting(context: Context) {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
    }

    fun openApplication(context: Context) {
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage("com.google.android.apps.maps")
        Log.i(TAG, "openApplication: $intent")
        if (intent != null) {
            context.startActivity(intent)
        } else {
            Log.e(TAG, "Application not found")

        }

    }

}