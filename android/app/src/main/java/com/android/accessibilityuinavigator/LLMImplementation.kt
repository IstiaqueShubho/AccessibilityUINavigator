package com.android.accessibilityuinavigator


import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.internal.concurrent.Task
import okio.Timeout
import java.sql.Time
import java.util.concurrent.TimeUnit


class LLMImplementation(private val serverUrl: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(0, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val gson = Gson()
    private val TAG = "LLMImplementation"

    suspend fun invokeSync(task: String): InvokeResponse? = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(mapOf("task" to task))
            val body = RequestBody.create("application/json".toMediaTypeOrNull(), json)
            val request = Request.Builder()
                .url("$serverUrl/invoke")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build()
            client.connectTimeoutMillis
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string().let {
                    gson.fromJson(it, InvokeResponse::class.java)
                }
            } else {
                Log.e(TAG, "Invoke failed: ${response.code} - ${response.body?.string()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "invoke error: ${e.printStackTrace()}")
            null
        }
    }


    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$serverUrl/health")
                .get()
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful.also {
                Log.i(TAG, "Health check: ${if (it) "OK" else "Failed"}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Health check error: ${e.message}")
            false
        }
    }
}


data class InvokeResponse(val result: Map<String, Any>?)
