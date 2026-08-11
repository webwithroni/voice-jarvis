package com.webwithroni.voicejarvis

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GroqClient {

    private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    private const val MODEL = "llama-3.3-70b-versatile"

    private const val SYSTEM_PROMPT =
        "You are Jarvis, a helpful personal voice assistant for Roni. " +
        "Reply in the same mix of Hindi, Bengali, or English that the user used. " +
        "Keep replies short and conversational, suitable for being spoken aloud. " +
        "Avoid long lists or markdown formatting."

    fun ask(userText: String, onResult: (String) -> Unit, onError: (String) -> Unit) {
        Thread {
            try {
                val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 20000

                val messages = JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                    put(JSONObject().put("role", "user").put("content", userText))
                }

                val body = JSONObject().apply {
                    put("model", MODEL)
                    put("messages", messages)
                    put("temperature", 0.7)
                }

                conn.outputStream.use { it.write(body.toString().toByteArray()) }

                if (conn.responseCode in 200..299) {
                    val responseText = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(responseText)
                    val reply = json.getJSONArray("choices")
                        .getJSONObject(0).getJSONObject("message").getString("content")
                    onResult(reply.trim())
                } else {
                    val errText = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP ${conn.responseCode}"
                    onError("Groq error: $errText")
                }
            } catch (e: Exception) {
                onError("Groq exception: ${e.javaClass.simpleName}: ${e.message}")
            }
        }.start()
    }
}
