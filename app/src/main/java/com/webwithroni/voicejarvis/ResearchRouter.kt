package com.webwithroni.voicejarvis

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.time.LocalDate
import java.util.concurrent.Executors

/**
 * Voice Jarvis research pipeline.
 *
 * Normal conversation NEVER comes through this class.
 *
 * Deep research:
 *
 * 1. Tavily retrieves current multi-source evidence.
 * 2. Sources are normalized and deduplicated.
 * 3. OpenRouter Auto synthesizes the evidence.
 * 4. Groq / DeepSeek are used only if synthesis fails.
 *
 * The returned JSON separates:
 *
 * - voiceAnswer     -> short, natural answer for Jarvis speech
 * - detailedAnswer  -> richer answer for UI/history
 * - confidence      -> high / medium / low
 * - sources         -> source metadata
 */
object ResearchRouter {

    private const val TAVILY_URL =
        "https://api.tavily.com/search"

    private const val OPENROUTER_URL =
        "https://openrouter.ai/api/v1/chat/completions"

    private const val OPENROUTER_MODEL =
        "openrouter/auto"

    private const val MAX_SOURCES =
        8

    private const val MAX_SOURCE_CONTENT =
        5000

    private data class SynthesisProvider(
        val name: String,
        val url: String,
        val key: String,
        val model: String
    )

    private fun synthesisProviders():
        List<SynthesisProvider> {

        return listOf(

            SynthesisProvider(
                name = "OpenRouter",
                url = OPENROUTER_URL,
                key = BuildConfig.OPENROUTER_API_KEY,
                model = OPENROUTER_MODEL
            ),

            SynthesisProvider(
                name = "Groq",
                url =
                    "https://api.groq.com/openai/v1/chat/completions",
                key = BuildConfig.GROQ_API_KEY,
                model = "llama-3.3-70b-versatile"
            ),

            SynthesisProvider(
                name = "DeepSeek",
                url =
                    "https://api.deepseek.com/chat/completions",
                key = BuildConfig.DEEPSEEK_API_KEY,
                model = "deepseek-chat"
            )

        ).filter {
            it.key.isNotBlank()
        }
    }

    fun research(
        query: String
    ): JSONObject {

        val cleanQuery =
            normalizeQuery(query)

        if (cleanQuery.isBlank()) {
            return error(
                "Research query was empty."
            )
        }

        if (
            BuildConfig.TAVILY_API_KEY.isBlank()
        ) {
            return error(
                "Tavily is not configured."
            )
        }

        return try {

            val rawSources =
                searchResearchSources(
                    cleanQuery
                )

            val sources =
                normalizeSources(
                    rawSources
                )

            if (
                sources.length() == 0
            ) {

                return error(
                    "No useful research sources were found."
                )
            }

            val synthesis =
                synthesize(
                    query = cleanQuery,
                    sources = sources
                )

            if (
                synthesis == null
            ) {

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
                    "voiceAnswer",
                    synthesis.voiceAnswer
                )

                put(
                    "answer",
                    synthesis.voiceAnswer
                )

                put(
                    "detailedAnswer",
                    synthesis.detailedAnswer
                )

                put(
                    "confidence",
                    synthesis.confidence
                )

                put(
                    "sourceCount",
                    sources.length()
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

    private fun searchResearchSources(
        query: String
    ): JSONArray {

        val queries =
            buildResearchQueries(query)

        /*
         * Research queries are independent, so run them
         * concurrently. This keeps deep research latency
         * bounded instead of waiting for every search
         * sequentially.
         */
        val executor =
            Executors.newFixedThreadPool(
                minOf(
                    3,
                    queries.size
                )
            )

        return try {

            val futures =
                queries.map { researchQuery ->

                    executor.submit<JSONArray> {
                        searchTavily(
                            researchQuery
                        )
                    }
                }

            val merged =
                JSONArray()

            futures.forEach { future ->

                try {

                    val results =
                        future.get()

                    for (
                        i in 0 until results.length()
                    ) {

                        results
                            .optJSONObject(i)
                            ?.let {
                                merged.put(it)
                            }
                    }

                } catch (_: Exception) {
                    /*
                     * One search failing should not kill
                     * the entire research request.
                     */
                }
            }

            merged

        } finally {

            executor.shutdownNow()
        }
    }

    private fun buildResearchQueries(
        query: String
    ): List<String> {

        val lower =
            query.lowercase(Locale.US)

        val news =
            isNewsQuery(query)

        if (!news) {
            return listOf(
                query
            )
        }

        val date =
            LocalDate.now()

        val monthYear =
            "${date.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${date.year}"

        return listOf(
            query,

            "$query concrete company announcements model launches products ${monthYear}",

            "$query AI regulation safety research startups breakthroughs ${monthYear}"
        ).distinct()
    }

    private fun normalizeQuery(
        query: String
    ): String {

        return query
            .trim()
            .replace(
                Regex("\\s+"),
                " "
            )
            .take(1000)
    }

    private fun isNewsQuery(
        query: String
    ): Boolean {

        val lower =
            query.lowercase(Locale.US)

        return listOf(
            "latest",
            "news",
            "today",
            "current",
            "recent",
            "this week",
            "breaking",
            "what happened",
            "recent developments"
        ).any {
            lower.contains(it)
        }
    }

    private fun searchTavily(
        query: String
    ): JSONArray {

        val conn =
            URL(TAVILY_URL)
                .openConnection() as HttpURLConnection

        try {

            conn.requestMethod =
                "POST"

            conn.setRequestProperty(
                "Content-Type",
                "application/json"
            )

            conn.setRequestProperty(
                "Authorization",
                "Bearer ${BuildConfig.TAVILY_API_KEY}"
            )

            conn.connectTimeout =
                10_000

            conn.readTimeout =
                25_000

            conn.doOutput =
                true

            val news =
                isNewsQuery(query)

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
                        MAX_SOURCES
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
                        if (news) {
                            "news"
                        } else {
                            "general"
                        }
                    )

                    if (news) {

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
                    body
                        .toString()
                        .toByteArray(
                            Charsets.UTF_8
                        )
                )
            }

            if (
                conn.responseCode !in 200..299
            ) {

                throw IllegalStateException(
                    "Tavily HTTP ${conn.responseCode}"
                )
            }

            val response =
                JSONObject(
                    conn.inputStream
                        .bufferedReader()
                        .readText()
                )

            return response.optJSONArray(
                "results"
            ) ?: JSONArray()

        } finally {

            conn.disconnect()
        }
    }

    private fun normalizeSources(
        results: JSONArray
    ): JSONArray {

        val output =
            JSONArray()

        val seenUrls =
            HashSet<String>()

        val seenDomains =
            HashSet<String>()

        /*
         * First pass:
         * prioritize unique URLs and unique domains.
         */
        for (
            i in 0 until results.length()
        ) {

            if (
                output.length() >= MAX_SOURCES
            ) {
                break
            }

            val item =
                results.optJSONObject(i)
                    ?: continue

            val title =
                item.optString(
                    "title"
                ).trim()

            val url =
                item.optString(
                    "url"
                ).trim()

            val content =
                item.optString(
                    "content"
                ).trim()

            val raw =
                item.optString(
                    "raw_content"
                ).trim()

            val sourceText =
                if (
                    raw.isNotBlank()
                ) {
                    raw
                } else {
                    content
                }

            if (
                title.isBlank() ||
                sourceText.isBlank()
            ) {
                continue
            }

            val normalizedUrl =
                normalizeUrl(
                    url
                )

            if (
                normalizedUrl.isBlank() ||
                seenUrls.contains(
                    normalizedUrl
                )
            ) {
                continue
            }

            val domain =
                extractDomain(
                    normalizedUrl
                )

            /*
             * Prefer source diversity.
             */
            if (
                domain.isNotBlank() &&
                seenDomains.contains(domain) &&
                output.length() >= 4
            ) {
                continue
            }

            seenUrls.add(
                normalizedUrl
            )

            if (
                domain.isNotBlank()
            ) {
                seenDomains.add(
                    domain
                )
            }

            output.put(
                JSONObject().apply {

                    put(
                        "rank",
                        output.length() + 1
                    )

                    put(
                        "title",
                        title
                    )

                    put(
                        "url",
                        normalizedUrl
                    )

                    put(
                        "domain",
                        domain
                    )

                    put(
                        "content",
                        sourceText.take(
                            MAX_SOURCE_CONTENT
                        )
                    )
                }
            )
        }

        /*
         * If diversity removed too many sources,
         * fill remaining slots using unique URLs.
         */
        if (
            output.length() < MAX_SOURCES
        ) {

            for (
                i in 0 until results.length()
            ) {

                if (
                    output.length() >= MAX_SOURCES
                ) {
                    break
                }

                val item =
                    results.optJSONObject(i)
                        ?: continue

                val title =
                    item.optString(
                        "title"
                    ).trim()

                val url =
                    normalizeUrl(
                        item.optString(
                            "url"
                        )
                    )

                val content =
                    item.optString(
                        "raw_content"
                    )
                        .ifBlank {
                            item.optString(
                                "content"
                            )
                        }
                        .trim()

                if (
                    title.isBlank() ||
                    url.isBlank() ||
                    content.isBlank()
                ) {
                    continue
                }

                if (
                    seenUrls.add(url)
                ) {

                    output.put(
                        JSONObject().apply {

                            put(
                                "rank",
                                output.length() + 1
                            )

                            put(
                                "title",
                                title
                            )

                            put(
                                "url",
                                url
                            )

                            put(
                                "domain",
                                extractDomain(url)
                            )

                            put(
                                "content",
                                content.take(
                                    MAX_SOURCE_CONTENT
                                )
                            )
                        }
                    )
                }
            }
        }

        return output
    }

    private fun normalizeUrl(
        url: String
    ): String {

        return url
            .trim()
            .removeSuffix("/")
    }

    private fun extractDomain(
        url: String
    ): String {

        return try {

            URL(url).host
                .lowercase(Locale.US)
                .removePrefix(
                    "www."
                )

        } catch (_: Exception) {

            ""
        }
    }

    private data class SynthesisResult(
        val voiceAnswer: String,
        val detailedAnswer: String,
        val confidence: String
    )

    private fun synthesize(
        query: String,
        sources: JSONArray
    ): SynthesisResult? {

        val sourceText =
            buildString {

                for (
                    i in 0 until sources.length()
                ) {

                    val source =
                        sources.optJSONObject(i)
                            ?: continue

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
                        "\nDOMAIN: "
                    )

                    append(
                        source.optString(
                            "domain"
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

        val systemPrompt =
            """
            You are Voice Jarvis's grounded research
            synthesis engine.

            You receive retrieved web sources.

            Your job is NOT to invent information.

            HARD RULES:

            1. Use ONLY information supported by the
               supplied sources.

            2. Never invent dates, numbers, quotes,
               organizations, products, launches or events.

            3. Prefer the newest and most authoritative
               evidence when sources disagree.

            4. Do NOT turn a collection of stories into a
               generic trend paragraph.

            5. Every concrete news item MUST contain:
               - a named organization, person or product
               - a specific event/development
               - a source reference such as [1] or [2]

            6. If a claim cannot be tied to a specific
               supplied source, omit it.

            7. If several sources describe the same story,
               merge them into ONE story instead of repeating it.

            8. Produce exactly valid JSON:

               {
                 "voiceAnswer": "...",
                 "detailedAnswer": "...",
                 "confidence": "high|medium|low"
               }

            9. voiceAnswer:
               - natural spoken answer
               - maximum 6 short sentences
               - start directly with the answer
               - mention the most important named stories
               - no URLs
               - no markdown table

            10. detailedAnswer:
                - 3 to 5 numbered concrete developments
                - each item must identify WHAT happened
                - each item must identify WHO/WHAT is involved
                - explain WHY it matters
                - include supported dates when available
                - include source references [1], [2], etc.
                - end with an overall takeaway

            11. For latest/news questions:
                - prioritize developments from the requested
                  time period
                - prefer concrete announcements, launches,
                  policy actions, research results and funding
                - avoid vague statements like
                  "AI investment is increasing" unless a
                  specific source-backed investment is named

            12. If fewer than 3 concrete stories are supported,
                return only the stories that are actually supported.

            13. If evidence is weak or contradictory:
                confidence = "low" or "medium"

            14. If the sources strongly agree on multiple
                concrete stories:
                confidence = "high"

            USER QUESTION:
            $query

            RETRIEVAL NOTE:
            Multiple independent search angles may be present.
            Treat duplicate coverage of the same event as one
            story and use the strongest sources as evidence.

            SOURCES:
            $sourceText
            """.trimIndent()

        var lastError = ""

        for (
            provider in synthesisProviders()
        ) {

            try {

                val response =
                    callSynthesisProvider(
                        provider,
                        systemPrompt
                    )

                if (
                    response.isNullOrBlank()
                ) {
                    continue
                }

                val parsed =
                    parseSynthesis(
                        response
                    )

                if (
                    parsed != null &&
                    parsed.voiceAnswer.isNotBlank()
                ) {
                    return parsed
                }

            } catch (e: Exception) {

                lastError =
                    "${provider.name}: ${e.message}"
            }
        }

        return null
    }

    private fun callSynthesisProvider(
        provider: SynthesisProvider,
        systemPrompt: String
    ): String? {

        val conn =
            URL(provider.url)
                .openConnection() as HttpURLConnection

        try {

            conn.requestMethod =
                "POST"

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

            conn.connectTimeout =
                10_000

            conn.readTimeout =
                45_000

            conn.doOutput =
                true

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
                        0.15
                    )

                    put(
                        "max_tokens",
                        1400
                    )
                }

            conn.outputStream.use {
                it.write(
                    body
                        .toString()
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
                ?.optJSONObject(
                    "message"
                )
                ?.optString(
                    "content"
                )
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }

        } finally {

            conn.disconnect()
        }
    }

    private fun parseSynthesis(
        raw: String
    ): SynthesisResult? {

        /*
         * Strip accidental markdown fences.
         */
        val cleaned =
            raw
                .trim()
                .removePrefix(
                    "```json"
                )
                .removePrefix(
                    "```"
                )
                .removeSuffix(
                    "```"
                )
                .trim()

        val json =
            try {

                JSONObject(
                    cleaned
                )

            } catch (_: Exception) {

                /*
                 * Some models prepend text before JSON.
                 * Recover the first JSON object.
                 */
                val start =
                    cleaned.indexOf("{")

                val end =
                    cleaned.lastIndexOf("}")

                if (
                    start < 0 ||
                    end <= start
                ) {
                    return null
                }

                try {

                    JSONObject(
                        cleaned.substring(
                            start,
                            end + 1
                        )
                    )

                } catch (_: Exception) {

                    return null
                }
            }

        val voice =
            json.optString(
                "voiceAnswer"
            ).trim()

        val detailed =
            json.optString(
                "detailedAnswer"
            )
                .ifBlank {
                    voice
                }
                .trim()

        val confidence =
            when (
                json.optString(
                    "confidence"
                )
                    .lowercase(Locale.US)
                    .trim()
            ) {

                "high" ->
                    "high"

                "medium" ->
                    "medium"

                else ->
                    "low"
            }

        if (
            voice.isBlank()
        ) {
            return null
        }

        return SynthesisResult(
            voiceAnswer =
                voice.take(3000),
            detailedAnswer =
                detailed.take(8000),
            confidence =
                confidence
        )
    }

    private fun error(
        message: String
    ): JSONObject {

        return JSONObject().apply {

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

            put(
                "confidence",
                "low"
            )
        }
    }
}
