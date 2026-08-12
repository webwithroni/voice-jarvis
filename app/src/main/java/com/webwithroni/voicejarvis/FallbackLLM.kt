package com.webwithroni.voicejarvis

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object FallbackLLM {

    private data class Provider(val name: String, val url: String, val key: String, val model: String)

    private fun providers(): List<Provider> = listOf(
        Provider("Groq", "https://api.groq.com/openai/v1/chat/completions", BuildConfig.GROQ_API_KEY, "llama-3.3-70b-versatile"),
        Provider("OpenRouter", "https://openrouter.ai/api/v1/chat/completions", BuildConfig.OPENROUTER_API_KEY, "meta-llama/llama-3.3-70b-instruct:free"),
        Provider("DeepSeek", "https://api.deepseek.com/chat/completions", BuildConfig.DEEPSEEK_API_KEY, "deepseek-chat")
    ).filter { it.key.isNotBlank() }

    fun ask(systemPrompt: String, userText: String, onResult: (String) -> Unit, onError: (String) -> Unit) {
        Thread {
            val list = providers()
            if (list.isEmpty()) { onError("No fallback provider configured"); return@Thread }
            var lastError = ""
            for (p in list) {
                try {
                    val reply = callProvider(p, systemPrompt, userText)
                    if (reply != null) { onResult(reply); return@Thread }
                } catch (e: Exception) {
                    lastError = p.name + ": " + e.message
                }
            }
            onError("All fallback providers failed. $lastError")
        }.start()
    }

    private fun callProvider(p: Provider, systemPrompt: String, userText: String): String? {
        val conn = URL(p.url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer " + p.key)
        conn.doOutput = true
        conn.connectTimeout = 10000
        conn.readTimeout = 15000

        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            put(JSONObject().put("role", "user").put("content", userText))
        }
        val body = JSONObject().apply {
            put("model", p.model)
            put("messages", messages)
            put("temperature", 0.7)
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        if (conn.responseCode !in 200..299) return null
        val responseText = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(responseText)
        return json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
    }
}
