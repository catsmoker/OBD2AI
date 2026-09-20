package com.catsmoker.obd2ai.ai

import android.content.Context
import android.util.Log
import com.catsmoker.obd2ai.diagnostics.DtcInfo
import com.catsmoker.obd2ai.diagnostics.DtpCodeDTO
import com.catsmoker.obd2ai.diagnostics.ErrorSeverity
import com.catsmoker.obd2ai.diagnostics.MilStatus
import com.catsmoker.obd2ai.prefs.PrefsKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A selectable AI backend for DTC assessments. */
enum class AiProvider(
    val id: String,
    val displayName: String,
    val defaultModel: String,
    val defaultBaseUrl: String,
    /** Cloud providers refuse keyless calls; local/custom ones may allow them. */
    val needsKey: Boolean,
    val showBaseUrl: Boolean
) {
    OPENAI(
        "openai", "OpenAI",
        // No free API tier; gpt-4o-mini is the cheapest general-purpose model.
        "gpt-4o-mini", "https://api.openai.com/v1",
        needsKey = true, showBaseUrl = false
    ),
    GEMINI(
        "gemini", "Google Gemini",
        // Free tier via AI Studio (no card). 2.5-flash retires Oct 2026,
        // so default to the free-tier 3.x Flash-Lite line.
        "gemini-3.1-flash-lite", "https://generativelanguage.googleapis.com/v1beta",
        needsKey = true, showBaseUrl = false
    ),
    ANTHROPIC(
        "anthropic", "Anthropic",
        // Paid-only API; Haiku is the cheapest Claude tier.
        "claude-haiku-4-5", "https://api.anthropic.com/v1",
        needsKey = true, showBaseUrl = false
    ),
    CUSTOM(
        "custom", "Custom (OpenAI-compatible)",
        // Local servers (Ollama, LM Studio, …) are free; llama3.2 runs on modest hardware.
        "llama3.2", "",
        needsKey = false, showBaseUrl = true
    );

    companion object {
        fun fromId(id: String?): AiProvider =
            entries.firstOrNull { it.id == id } ?: OPENAI
    }
}

data class AiConfig(
    val provider: AiProvider,
    val apiKey: String,
    val model: String,
    val baseUrl: String
)

/**
 * Fault-code explanations from any chat-capable AI. The API key is used
 * ONLY here: driving-voice cues (Offline AI) are fully offline (bundled clips
 * + on-device TTS) and never touch the network. Cloud providers and local
 * OpenAI-compatible servers (Ollama, LM Studio, llama.cpp, OpenRouter, …)
 * are covered; each provider speaks its native HTTP API directly so no
 * vendor SDK is needed.
 */
class AiService(private val context: Context) {

    fun getConfig(): AiConfig {
        val prefs = context.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        val provider = AiProvider.fromId(prefs.getString(PrefsKeys.AI_PROVIDER, null))
        val apiKey = prefs.getString(PrefsKeys.OPENAI_API_KEY, "").orEmpty().trim()
        val model = prefs.getString(PrefsKeys.OPENAI_MODEL_ID, "").orEmpty().trim()
            .ifEmpty { provider.defaultModel }
        val baseUrl = prefs.getString(PrefsKeys.AI_BASE_URL, "").orEmpty().trim()
            .ifEmpty { provider.defaultBaseUrl }
        return AiConfig(provider, apiKey, model, baseUrl)
    }

    fun hasApiKey(): Boolean {
        val config = getConfig()
        return !config.provider.needsKey || config.apiKey.isNotEmpty()
    }

    suspend fun getDtpCodeAssessment(dtpCode: String): DtpCodeDTO = withContext(Dispatchers.IO) {
        val config = getConfig()
        if (config.provider.needsKey && config.apiKey.isEmpty()) {
            throw IllegalStateException("No API key configured. Set one in Settings.")
        }
        if (config.provider.showBaseUrl && config.baseUrl.isEmpty()) {
            throw IllegalStateException("No server URL configured. Set one in Settings.")
        }
        val assessmentJson = when (config.provider) {
            AiProvider.OPENAI, AiProvider.CUSTOM -> postChatCompletions(config, dtpCode)
            AiProvider.GEMINI -> postGemini(config, dtpCode)
            AiProvider.ANTHROPIC -> postAnthropic(config, dtpCode)
        }
        parseErrorInfo(assessmentJson)
    }

    /**
     * Free-form chat for the Online AI driving assistant. Returns plain
     * speakable text (never JSON), trimmed to a safe length.
     */
    suspend fun chatText(systemPrompt: String, userText: String, maxTokens: Int = 160): String =
        withContext(Dispatchers.IO) {
            val config = getConfig()
            if (config.provider.needsKey && config.apiKey.isEmpty()) {
                throw IllegalStateException("No API key configured. Set one in Settings.")
            }
            if (config.provider.showBaseUrl && config.baseUrl.isEmpty()) {
                throw IllegalStateException("No server URL configured. Set one in Settings.")
            }
            val raw = when (config.provider) {
                AiProvider.OPENAI, AiProvider.CUSTOM ->
                    parseOpenAiCompatibleResponse(postChatText(config, systemPrompt, userText, maxTokens))
                AiProvider.GEMINI ->
                    parseGeminiResponse(postGeminiText(config, systemPrompt, userText))
                AiProvider.ANTHROPIC ->
                    parseAnthropicResponse(postAnthropicText(config, systemPrompt, userText, maxTokens))
            }
            sanitizeSpoken(raw)
        }

    private fun postChatText(config: AiConfig, system: String, user: String, maxTokens: Int): String {
        val body = JSONObject()
            .put("model", config.model)
            .put("max_tokens", maxTokens)
            .put("messages", org.json.JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", user)))
            .toString()
        val headers = mutableMapOf("Content-Type" to "application/json")
        if (config.apiKey.isNotEmpty()) {
            headers["Authorization"] = "Bearer ${config.apiKey}"
        }
        return post(config.baseUrl.trimEnd('/') + "/chat/completions", headers, body)
    }

    private fun postGeminiText(config: AiConfig, system: String, user: String): String {
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", org.json.JSONArray()
                .put(JSONObject().put("text", system))))
            .put("contents", org.json.JSONArray()
                .put(JSONObject().put("parts", org.json.JSONArray()
                    .put(JSONObject().put("text", user)))))
            .toString()
        val encodedKey = URLEncoder.encode(config.apiKey, "UTF-8")
        val url = config.baseUrl.trimEnd('/') + "/models/${config.model}:generateContent?key=$encodedKey"
        return post(url, mapOf("Content-Type" to "application/json"), body)
    }

    private fun postAnthropicText(config: AiConfig, system: String, user: String, maxTokens: Int): String {
        val body = JSONObject()
            .put("model", config.model)
            .put("max_tokens", maxTokens)
            .put("system", system)
            .put("messages", org.json.JSONArray()
                .put(JSONObject().put("role", "user").put("content", user)))
            .toString()
        val headers = mapOf(
            "Content-Type" to "application/json",
            "x-api-key" to config.apiKey,
            "anthropic-version" to "2023-06-01"
        )
        return post(config.baseUrl.trimEnd('/') + "/messages", headers, body)
    }

    private fun postChatCompletions(config: AiConfig, dtpCode: String): String {
        val body = JSONObject()
            .put("model", config.model)
            .put("messages", org.json.JSONArray()
                .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                .put(JSONObject().put("role", "user").put("content", dtpCode)))
            .toString()
        val headers = mutableMapOf("Content-Type" to "application/json")
        if (config.apiKey.isNotEmpty()) {
            headers["Authorization"] = "Bearer ${config.apiKey}"
        }
        val url = config.baseUrl.trimEnd('/') + "/chat/completions"
        return parseOpenAiCompatibleResponse(post(url, headers, body))
    }

    private fun postGemini(config: AiConfig, dtpCode: String): String {
        val body = JSONObject()
            .put("contents", org.json.JSONArray()
                .put(JSONObject().put("parts", org.json.JSONArray()
                    .put(JSONObject().put("text", "$SYSTEM_PROMPT\n\n$dtpCode")))))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
            .toString()
        val encodedKey = URLEncoder.encode(config.apiKey, "UTF-8")
        val url = config.baseUrl.trimEnd('/') + "/models/${config.model}:generateContent?key=$encodedKey"
        return parseGeminiResponse(post(url, mapOf("Content-Type" to "application/json"), body))
    }

    private fun postAnthropic(config: AiConfig, dtpCode: String): String {
        val body = JSONObject()
            .put("model", config.model)
            .put("max_tokens", 1024)
            .put("system", SYSTEM_PROMPT)
            .put("messages", org.json.JSONArray()
                .put(JSONObject().put("role", "user").put("content", dtpCode)))
            .toString()
        val headers = mapOf(
            "Content-Type" to "application/json",
            "x-api-key" to config.apiKey,
            "anthropic-version" to "2023-06-01"
        )
        val url = config.baseUrl.trimEnd('/') + "/messages"
        return parseAnthropicResponse(post(url, headers, body))
    }

    private fun post(url: String, headers: Map<String, String>, body: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15000
            connection.readTimeout = 60000
            connection.requestMethod = "POST"
            connection.doOutput = true
            for ((name, value) in headers) {
                connection.setRequestProperty(name, value)
            }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val errorBody = runCatching {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }
                }.getOrNull().orEmpty().take(500)
                throw IOException("AI request failed: HTTP $code. $errorBody".trim())
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        internal const val SYSTEM_PROMPT =
            "You are an expert mechanic. Given a plain OBD2 error code, provide a resolution " +
                "in a structured JSON format with fields: 'errorCode', 'severity' (0-Low, 1-Medium, 2-High), " +
                "'title' (max 60 chars), 'detail' (~300 chars), 'implications' (~300 chars), and " +
                "'suggestedActions' (array of strings). Reply with JSON only, no markdown fences."

        internal fun parseErrorInfo(jsonString: String): DtpCodeDTO {
            return try {
                val jsonObject = JSONObject(extractJsonObject(jsonString))
                val errorCode = jsonObject.getString("errorCode")
                val severity = ErrorSeverity.fromInt(jsonObject.getInt("severity"))
                val title = jsonObject.getString("title")
                val detail = jsonObject.getString("detail")
                val implications = jsonObject.getString("implications")
                val actionsArray = jsonObject.getJSONArray("suggestedActions")
                val suggestedActions = (0 until actionsArray.length()).map { actionsArray.getString(it) }
                DtpCodeDTO(errorCode, severity, title, detail, implications, suggestedActions)
            } catch (e: Exception) {
                Log.e("AiService", "Failed to parse JSON response: $jsonString", e)
                DtpCodeDTO("Error", ErrorSeverity.LOW, "Parsing Error", "Could not parse server response.", "Invalid data.", listOf("Try again."))
            }
        }

        /**
         * Local/chatty models often wrap JSON in ``` fences or prose. Extract the
         * outermost {...} so strict models and chatty ones both parse.
         */
        internal fun extractJsonObject(text: String): String {
            val unfenced = text.replace("```json", "").replace("```", "").trim()
            val start = unfenced.indexOf('{')
            val end = unfenced.lastIndexOf('}')
            return if (start >= 0 && end > start) unfenced.substring(start, end + 1) else text
        }

        /**
         * Collapses model chatter into one speakable line: strips markdown
         * artifacts, squeezes whitespace, caps length (cost + TTS sanity).
         */
        internal fun sanitizeSpoken(text: String, maxChars: Int = 400): String {
            var clean = text.replace("```", "")
            clean = clean.replace(Regex("[*_#>`]"), "")
            clean = clean.replace(Regex("\\s+"), " ").trim()
            return if (clean.length > maxChars) clean.take(maxChars).trimEnd() + "…" else clean
        }

        internal fun parseOpenAiCompatibleResponse(jsonString: String): String {
            return try {
                JSONObject(jsonString)
                    .getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content")
            } catch (e: Exception) {
                Log.e("AiService", "Failed to parse chat-completions response", e)
                "{}"
            }
        }

        internal fun parseGeminiResponse(jsonString: String): String {
            return try {
                JSONObject(jsonString)
                    .getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts")
                    .getJSONObject(0).getString("text")
            } catch (e: Exception) {
                Log.e("AiService", "Failed to parse Gemini response", e)
                "{}"
            }
        }

        internal fun parseAnthropicResponse(jsonString: String): String {
            return try {
                JSONObject(jsonString)
                    .getJSONArray("content").getJSONObject(0).getString("text")
            } catch (e: Exception) {
                Log.e("AiService", "Failed to parse Anthropic response", e)
                "{}"
            }
        }

        /**
         * Offline fallback when no API key is set or a network request fails.
         * With a [DtcInfo] entry (bundled dictionary) the answer is specific;
         * without one it decodes only what the code letters guarantee (system
         * + generic/manufacturer origin per SAE J2012) so the report stays
         * useful without inventing details.
         */
        fun buildOfflineAssessment(rawCode: String, dict: DtcInfo? = null): DtpCodeDTO {
            val code = rawCode.trim().uppercase()
            if (dict != null) {
                return DtpCodeDTO(
                    errorCode = code,
                    severity = ErrorSeverity.MEDIUM,
                    title = dict.title,
                    detail = "Offline info: ${dict.detail}",
                    implications = dict.implications,
                    suggestedActions = dict.actions,
                    offline = true
                )
            }
            val system = when (code.firstOrNull()) {
                'P' -> "Powertrain (engine, transmission, emissions)"
                'C' -> "Chassis (ABS, steering, suspension)"
                'B' -> "Body (airbags, lighting, comfort electronics)"
                'U' -> "Network (ECU communication)"
                else -> "Unknown system"
            }
            val origin = when (code.getOrNull(1)) {
                '0', '2' -> "Generic (SAE standard)"
                '1' -> "Manufacturer-specific"
                '3' -> "Reserved range"
                else -> "Unknown origin"
            }
            return DtpCodeDTO(
                errorCode = code,
                severity = ErrorSeverity.MEDIUM,
                title = "$code · ${system.substringBefore(" (")} fault",
                detail = "Offline info: $code is an $origin code in the $system area. " +
                    "Add an AI API key in Settings for a full assessment.",
                implications = "If the engine light is on or you notice symptoms, have the " +
                    "vehicle checked and quote code $code.",
                suggestedActions = listOf(
                    "Note code $code and re-scan after a drive cycle to see if it returns",
                    "Add an AI API key in Settings for detailed guidance",
                    "Ask a mechanic, quoting code $code"
                ),
                offline = true
            )
        }

        /**
         * System prompt for the "Ask AI" follow-up chat on the fault-detail
         * screen. It replays the assessment the user already saw plus the
         * vehicle state, so follow-up answers build on that context instead
         * of starting from zero. Pure logic, tested.
         */
        internal fun buildFollowUpSystemPrompt(
            dto: DtpCodeDTO,
            mil: MilStatus?,
            vin: String?
        ): String {
            val lamp = when {
                mil == null -> "The engine light state is unknown."
                mil.milOn -> "The engine light is ON (${mil.storedCount} stored code(s) reported by ECU)."
                else -> "The engine light is OFF."
            }
            val vinLine = if (!vin.isNullOrBlank()) " VIN $vin." else ""
            val actions = dto.suggestedActions.joinToString("; ").ifEmpty { "none listed" }
            return "You are an expert mechanic talking to an ordinary driver about their car. " +
                "The driver already received this diagnosis for fault code ${dto.errorCode} " +
                "(severity ${dto.severity}, titled ${dto.title}): " +
                "Detail: ${dto.detail} What it can mean: ${dto.implications} " +
                "Suggested actions: $actions. " +
                "Vehicle context: $lamp$vinLine " +
                "Answer the follow-up question in plain language, briefly, in a few short sentences. " +
                "Stay consistent with the diagnosis above and never invent new fault codes. " +
                "If the question is about safety or whether to keep driving, err on caution " +
                "and recommend a qualified mechanic when the fault may be serious."
        }

        /**
         * User message for one follow-up turn: a compact recap of the
         * assessment, the most recent exchanges (capped so long chats stay
         * cheap), then the new question. Pure logic, tested.
         */
        internal fun buildFollowUpUserText(
            dto: DtpCodeDTO,
            history: List<Pair<String, String>>,
            question: String,
            maxExchanges: Int = 3
        ): String {
            val recap = "Fault ${dto.errorCode}: ${dto.title}. ${dto.detail} " +
                "Implications: ${dto.implications}"
            val recent = history.takeLast(maxExchanges).joinToString("\n") { (q, a) ->
                "Q: $q\nA: $a"
            }
            return if (recent.isEmpty()) {
                "$recap\nNew question: $question\nAnswer it."
            } else {
                "$recap\nPrevious questions and answers (most recent last):\n$recent\n" +
                    "New question: $question\nAnswer it."
            }
        }
    }

    /**
     * One "Ask AI" follow-up turn about an already-assessed fault code.
     * Returns plain text (never JSON). Throws when the brain is unconfigured
     * or the network fails, so the UI can explain instead of guessing.
     */
    suspend fun askFollowUp(
        dto: DtpCodeDTO,
        mil: MilStatus?,
        vin: String?,
        history: List<Pair<String, String>>,
        question: String
    ): String = chatText(
        buildFollowUpSystemPrompt(dto, mil, vin),
        buildFollowUpUserText(dto, history, question),
        maxTokens = 300
    )
}
