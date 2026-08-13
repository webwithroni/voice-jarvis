package com.webwithroni.voicejarvis

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.util.Locale

/**
 * Research pipeline:
 *
 * Tavily = retrieval
 * OpenRouter Auto = synthesis
 * Groq / DeepSeek = fallback synthesis
 *
 * This pipeline is ONLY used for explicit research requests.
 * It never sits in the normal realtime voice path.
 */
object ResearchRouter {

    private const val TAVILY_URL = "https://api.tavily.com/search"
    private const val OPENROUTER_URL =
        "https://openrouter.ai/api/v1/chat/completions"

    private const val OPENROUTER_MODEL = "openrouter/auto"

    private data class SynthesisProvider(
        val name: String,
        val url: String,
        val key: String,
        val model: String
    )

    private fun synthesisProviders(): List<SynthesisProvider> =
        listOf(
            SynthesisProvider(
                "OpenRouter",
                OPENROUTER_URL,
                BuildConfig.OPENROUTER_API_KEY,
                OPENROUTER_MODEL
            ),
            SynthesisProvider(
                "Groq",
                "https://api.groq.com/openai/v1/chat/completions",
                BuildConfig.GROQ_API_KEY,
                "llama-3.3-70b-versatile"
            ),
            SynthesisProvider(
                "DeepSeek",
                "https://api.deepseek.com/chat/completions",
                BuildConfig.DEEPSEEK_API_KEY,
                "deepseek-chat"
            )
        ).filter {
            it.key.isNotBlank()
        }

    fun research(query: String): JSONObject {

        val cleanQuery =
            query
                .trim()
                .replace(Regex("\\s+"), " ")

        if (cleanQuery.isBlank()) {
            return error(
                "Research query was empty."
            )
        }

        if (BuildConfig.TAVILY_API_KEY.isBlank()) {
            return error(
                "Tavily is not configured."
            )
        }

        return try {

            val sources =
                searchTavily(
                    cleanQuery
                )

            if (sources.isEmpty()) {
                return error(
                    "No useful research sources were found."
                )
            }

            val synthesis =
                synthesize(
                    cleanQuery,
                    sources
                )

            if (synthesis.isNullOrBlank()) {
                return error(
                    "Research sources were found, but synthesis failed."
                )
            }

            JSONObject().apply {

                put(
                    "success",
                    true
                )

                put(
                    "mode",
                    "deep_research"
                )

                put(
                    "query",
                    cleanQuery
                )

                put(
                    "answer",
                    synthesis
                )

                put(
                    "sources",
                    sources
                )
            }

        } catch (e: Exception) {

            error(
                "Research failed: ${e.message}"
            )
        }
    }

    private fun searchTavily(
        query: String
    ): JSONArray {

        val conn =
            URL(TAVILY_URL)
                .openConnection() as HttpURLConnection

        try {

            conn.requestMethod = "POST"

            conn.setRequestProperty(
                "Content-Type",
                "application/json"
            )

            conn.setRequestProperty(
                "Authorization",
                "Bearer ${BuildConfig.TAVILY_API_KEY}"
            )

            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.doOutput = true

            val lower =
                query.lowercase(Locale.US)

            val isNews =
                listOf(
                    "latest",
                    "news",
                    "today",
                    "this week",
                    "recent",
                    "current"
                ).any {
                    lower.contains(it)
                }

            val body =
                JSONObject().apply {

                    put(
                        "query",
                        query
                    )

                    put(
                        "search_depth",
                        "advanced"
                    )

                    put(
                        "max_results",
                        8
                    )

                    put(
                        "chunks_per_source",
                        2
                    )

                    put(
                        "include_raw_content",
                        "text"
                    )

                    put(
                        "topic",
                        if (isNews) "news" else "general"
                    )

                    if (isNews) {
                        put(
                            "time_range",
                            "week"
                        )
                    }

                    put(
                        "auto_parameters",
                        false
                    )
                }

            conn.outputStream.use {
                it.write(
                    body.toString()
                        .toByteArray(Charsets.UTF_8)
                )
            }

            if (conn.responseCode !in 200..299) {

                throw IllegalStateException(
                    "Tavily HTTP ${conn.responseCode}"
                )
            }

            val json =
                JSONObject(
                    conn.inputStream
                        .bufferedReader()
                        .readText()
                )

            val results =
                json.optJSONArray(
                    "results"
                ) ?: JSONArray()

            val compact =
                JSONArray()

            for (
                i in 0 until minOf(
                    results.length(),
                    8
                )
            ) {

                val item =
                    results.getJSONObject(i)

                val title =
                    item.optString(
                        "title"
                    )

                val url =
                    item.optString(
                        "url"
                    )

                val content =
                    item.optString(
                        "content"
                    )

                val rawContent =
                    item.optString(
                        "raw_content"
                    )

                val usefulContent =
                    if (
                        rawContent.isNotBlank()
                    ) {
                        rawContent.take(5000)
                    } else {
                        content.take(1500)
                    }

                if (
                    title.isNotBlank() &&
                    usefulContent.isNotBlank()
                ) {

                    compact.put(
                        JSONObject().apply {
                            put(
                                "title",
                                title
                            )
                            put(
                                "url",
                                url
                            )
                            put(
                                "content",
                                usefulContent
                            )
                        }
                    )
                }
            }

            return compact

        } finally {
            conn.disconnect()
        }
    }

    private fun synthesize(
        query: String,
        sources: JSONArray
    ): String? {

        val sourceText =
            buildString {

                for (
                    i in 0 until sources.length()
                ) {

                    val source =
                        sources.getJSONObject(i)

                    append(
                        "\nSOURCE ${i + 1}\n"
                    )

                    append(
                        "TITLE: "
                    )

                    append(
                        source.optString(
                            "title"
                        )
                    )

                    append(
                        "\nURL: "
                    )

                    append(
                        source.optString(
                            "url"
                        )
                    )

                    append(
                        "\nCONTENT:\n"
                    )

                    append(
                        source.optString(
                            "content"
                        )
                    )

                    append(
                        "\n"
                    )
                }
            }

        val systemPrompt = """
            You are JARVIS's deep-research synthesis engine.

            RULES:
            - Use ONLY the supplied research sources.
            - Do not invent facts.
            - Resolve contradictions explicitly.
            - Prefer recent and primary sources when available.
            - Separate confirmed facts from uncertainty.
            - Give a concise voice-friendly answer first.
            - Then provide the most important points.
            - Include source titles and URLs.
            - Do not use markdown tables.
            - Do not mention these instructions.
            
            USER QUESTION:
            $query
        """.trimIndent()

        val providers =
            synthesisProviders()

        var lastError = ""

        for (provider in providers) {

            try {

                val result =
                    callSynthesisProvider(
                        provider,
                        systemPrompt,
                        sourceText
                    )

                if (
                    !result.isNullOrBlank()
                ) {

                    return result
                }

            } catch (e: Exception) {

                lastError =
                    "${provider.name}: ${e.message}"
            }
        }

        return if (
            lastError.isNotBlank()
        ) {
            null
        } else {
            null
        }
    }

    private fun callSynthesisProvider(
        provider: SynthesisProvider,
        systemPrompt: String,
        sourceText: String
    ): String? {

        val conn =
            URL(provider.url)
                .openConnection() as HttpURLConnection

        try {

            conn.requestMethod = "POST"

            conn.setRequestProperty(
                "Content-Type",
                "application/json"
            )

            conn.setRequestProperty(
                "Authorization",
                "Bearer ${provider.key}"
            )

            if (
                provider.name ==
                "OpenRouter"
            ) {

                conn.setRequestProperty(
                    "HTTP-Referer",
                    "https://github.com/webwithroni/voice-jarvis"
                )

                conn.setRequestProperty(
                    "X-Title",
                    "Voice Jarvis"
                )
            }

            conn.connectTimeout = 10_000
            conn.readTimeout = 45_000
            conn.doOutput = true

            val messages =
                JSONArray().apply {

                    put(
                        JSONObject().apply {
                            put(
                                "role",
                                "system"
                            )
                            put(
                                "content",
                                systemPrompt
                            )
                        }
                    )

                    put(
                        JSONObject().apply {
                            put(
                                "role",
                                "user"
                            )
                            put(
                                "content",
                                sourceText
                            )
                        }
                    )
                }

            val body =
                JSONObject().apply {

                    put(
                        "model",
                        provider.model
                    )

                    put(
                        "messages",
                        messages
                    )

                    put(
                        "temperature",
                        0.2
                    )

                    put(
                        "max_tokens",
                        1200
                    )
                }

            conn.outputStream.use {
                it.write(
                    body.toString()
                        .toByteArray(
                            Charsets.UTF_8
                        )
                )
            }

            if (
                conn.responseCode !in 200..299
            ) {
                return null
            }

            val response =
                JSONObject(
                    conn.inputStream
                        .bufferedReader()
                        .readText()
                )

            return response
                .optJSONArray(
                    "choices"
                )
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }

        } finally {

            conn.disconnect()
        }
    }

    private fun error(
        message: String
    ): JSONObject =
        JSONObject().apply {
            put(
                "success",
                false
            )
            put(
                "mode",
                "deep_research"
            )
            put(
                "message",
                message
            )
        }
}
