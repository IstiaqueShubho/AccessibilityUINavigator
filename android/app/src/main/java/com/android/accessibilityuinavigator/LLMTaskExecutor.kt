package com.android.accessibilityuinavigator

import android.util.Log
import com.google.gson.Gson
import java.net.ServerSocket

class LLMTaskExecutor (
    private val port: Int,
    private val onCallbackReceived: (CallbackPayload) -> Unit
){

    private val gson = Gson()
    private val tag = "CallbackServer"

    fun start() {
        Thread {
            try {
                val serverSocket = ServerSocket(port)
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


