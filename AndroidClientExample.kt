/**
 * Android Client Example - Kotlin
 * 
 * This demonstrates how to:
 * 1. Call the LLM server endpoints from Android
 * 2. Register a callback URL so the server can call you back
 * 3. Receive async results
 * 
 * Dependencies to add to build.gradle:
 *   implementation 'com.squareup.okhttp3:okhttp:4.11.0'
 *   implementation 'com.google.code.gson:gson:2.10.1'
 *   implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.6.2'
 *   implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1'
 */

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.IOException
import java.net.InetAddress

// Data models
data class InvokeResponse(val result: Map<String, Any>?)
data class RegisterCallbackRequest(val callback_url: String, val name: String = "default")
data class RegisterCallbackResponse(val registered: Boolean, val name: String, val callback_url: String)
data class InvokeAsyncRequest(val task: String, val callback_name: String = "default", val request_id: String? = null)
data class InvokeAsyncResponse(val started: Boolean, val callback_registered: Boolean)
data class CallbackPayload(val task: String, val result: Map<String, Any>?, val request_id: String?)

/**
 * LLM Server Client
 * Handles communication with the Python Flask server
 */
class LLMServerClient(private val serverUrl: String) {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val tag = "LLMClient"

    /**
     * Call the server synchronously - blocks until result is returned
     */
    suspend fun invokeSync(task: String): InvokeResponse? = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(mapOf("task" to task))
            val body = RequestBody.create(MediaType.parse("application/json"), json)
            val request = Request.Builder()
                .url("$serverUrl/invoke")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()?.let { 
                    gson.fromJson(it, InvokeResponse::class.java)
                }
            } else {
                Log.e(tag, "Invoke failed: ${response.code} - ${response.body?.string()}")
                null
            }
        } catch (e: Exception) {
            Log.e(tag, "Invoke error: ${e.message}", e)
            null
        }
    }

    /**
     * Call the server asynchronously - server will POST result to your callback URL
     */
    suspend fun invokeAsync(
        task: String,
        callbackName: String = "default",
        requestId: String? = null
    ): InvokeAsyncResponse? = withContext(Dispatchers.IO) {
        try {
            val req = InvokeAsyncRequest(task, callbackName, requestId)
            val json = gson.toJson(req)
            val body = RequestBody.create(MediaType.parse("application/json"), json)
            val request = Request.Builder()
                .url("$serverUrl/invoke_async")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()?.let {
                    gson.fromJson(it, InvokeAsyncResponse::class.java)
                }
            } else {
                Log.e(tag, "Async invoke failed: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(tag, "Async invoke error: ${e.message}", e)
            null
        }
    }

    /**
     * Register a callback URL with the server
     * The server will POST results to this URL when using invokeAsync
     */
    suspend fun registerCallback(callbackUrl: String, name: String = "default"): Boolean = 
        withContext(Dispatchers.IO) {
            try {
                val req = RegisterCallbackRequest(callbackUrl, name)
                val json = gson.toJson(req)
                val body = RequestBody.create(MediaType.parse("application/json"), json)
                val request = Request.Builder()
                    .url("$serverUrl/register_callback")
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    Log.i(tag, "Callback registered: $callbackUrl")
                    true
                } else {
                    Log.e(tag, "Register callback failed: ${response.code}")
                    false
                }
            } catch (e: Exception) {
                Log.e(tag, "Register callback error: ${e.message}", e)
                false
            }
        }

    /**
     * Health check - verify server is reachable
     */
    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$serverUrl/health")
                .get()
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful.also {
                Log.i(tag, "Health check: ${if (it) "OK" else "Failed"}")
            }
        } catch (e: Exception) {
            Log.e(tag, "Health check error: ${e.message}")
            false
        }
    }
}

/**
 * Simple HTTP Server to receive callbacks from the LLM server
 * 
 * Usage:
 *   val callbackServer = CallbackServer(8080) { payload ->
 *       Log.i("Callback", "Received: ${payload.result}")
 *   }
 *   callbackServer.start()
 *   val callbackUrl = "http://${getLocalIpAddress()}:8080/callback"
 *   llmClient.registerCallback(callbackUrl)
 *   
 *   llmClient.invokeAsync("Your task here", callbackName = "default")
 *   // Result will arrive as POST to http://your-device:8080/callback
 */
class CallbackServer(
    private val port: Int,
    private val onCallbackReceived: (CallbackPayload) -> Unit
) {
    private val gson = Gson()
    private val tag = "CallbackServer"

    fun start() {
        Thread {
            try {
                val serverSocket = java.net.ServerSocket(port)
                Log.i(tag, "Callback server listening on port $port")

                while (true) {
                    val socket = serverSocket.accept()
                    Thread {
                        try {
                            val reader = socket.getInputStream().bufferedReader()
                            val lines = mutableListOf<String>()
                            var line = reader.readLine()
                            while (line != null && line.isNotEmpty()) {
                                lines.add(line)
                                line = reader.readLine()
                            }

                            // Parse Content-Length header
                            var contentLength = 0
                            for (l in lines) {
                                if (l.startsWith("Content-Length:")) {
                                    contentLength = l.split(":")[1].trim().toInt()
                                }
                            }

                            // Read body
                            val body = CharArray(contentLength)
                            reader.read(body)
                            val jsonBody = String(body)

                            Log.d(tag, "Received callback: $jsonBody")

                            // Parse and process callback
                            val payload = gson.fromJson(jsonBody, CallbackPayload::class.java)
                            onCallbackReceived(payload)

                            // Send response
                            val response = "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n"
                            socket.getOutputStream().write(response.toByteArray())
                        } catch (e: Exception) {
                            Log.e(tag, "Error processing callback: ${e.message}", e)
                        } finally {
                            socket.close()
                        }
                    }.start()
                }
            } catch (e: Exception) {
                Log.e(tag, "Server error: ${e.message}", e)
            }
        }.start()
    }
}

/**
 * Utility to get local device IP address
 */
fun getLocalIpAddress(): String? {
    return try {
        InetAddress.getLocalHost().hostAddress
    } catch (ex: Exception) {
        null
    }
}

/**
 * Example Activity/Fragment showing how to use the client
 */
class ExampleUsage {
    suspend fun exampleSyncCall() {
        val client = LLMServerClient("http://192.168.1.101:8000")

        // Check if server is reachable
        val isHealthy = client.checkHealth()
        if (!isHealthy) {
            Log.e("Example", "Server not reachable")
            return
        }

        // Make a synchronous call - waits for result
        val response = client.invokeSync("Summarize the key points of machine learning.")
        if (response != null) {
            Log.i("Example", "Result: ${response.result}")
        }
    }

    suspend fun exampleAsyncCallWithCallback() {
        val client = LLMServerClient("http://192.168.1.101:8000")
        val localIp = getLocalIpAddress() ?: "127.0.0.1"

        // Start callback server on your device
        val callbackServer = CallbackServer(8080) { payload ->
            Log.i("Example", "Got async result: ${payload.result}")
            // Update UI here with the result
        }
        callbackServer.start()

        // Register callback URL
        val callbackUrl = "http://$localIp:8080/callback"
        client.registerCallback(callbackUrl, "default")

        // Make async call - result will arrive via callback
        val asyncResponse = client.invokeAsync(
            task = "What are the benefits of deep learning?",
            callbackName = "default",
            requestId = "req-123"
        )

        if (asyncResponse?.started == true) {
            Log.i("Example", "Async task started, waiting for callback...")
            // Result will arrive in the callback handler above
        }
    }
}
