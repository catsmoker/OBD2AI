package com.catsmoker.obd2ai.ai

import com.catsmoker.obd2ai.diagnostics.DtcInfo
import com.catsmoker.obd2ai.diagnostics.DtpCodeDTO
import com.catsmoker.obd2ai.diagnostics.ErrorSeverity
import com.catsmoker.obd2ai.diagnostics.MilStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for provider mapping, response parsing and the offline fallback. */
class AiServiceTest {

    @Test
    fun `parseErrorInfo parses a well-formed assessment`() {
        val json = """
            {
              "errorCode": "P0301",
              "severity": 2,
              "title": "Cylinder 1 Misfire",
              "detail": "Cylinder 1 is misfiring.",
              "implications": "Damage to catalytic converter possible.",
              "suggestedActions": ["Replace spark plug", "Check ignition coil"]
            }
        """.trimIndent()

        val dto = AiService.parseErrorInfo(json)

        assertEquals("P0301", dto.errorCode)
        assertEquals(ErrorSeverity.HIGH, dto.severity)
        assertEquals("Cylinder 1 Misfire", dto.title)
        assertEquals(listOf("Replace spark plug", "Check ignition coil"), dto.suggestedActions)
    }

    @Test
    fun `parseErrorInfo falls back to placeholder DTO on invalid JSON`() {
        val dto = AiService.parseErrorInfo("not json at all")

        assertEquals("Error", dto.errorCode)
        assertEquals(ErrorSeverity.LOW, dto.severity)
        assertEquals("Parsing Error", dto.title)
    }

    @Test
    fun `parseErrorInfo falls back to placeholder DTO on missing fields`() {
        val dto = AiService.parseErrorInfo("""{"errorCode": "P0301"}""")

        assertEquals("Error", dto.errorCode)
        assertEquals("Try again.", dto.suggestedActions.single())
    }

    @Test
    fun `AiProvider fromId maps known ids and defaults to OpenAI`() {
        assertEquals(AiProvider.OPENAI, AiProvider.fromId("openai"))
        assertEquals(AiProvider.GEMINI, AiProvider.fromId("gemini"))
        assertEquals(AiProvider.ANTHROPIC, AiProvider.fromId("anthropic"))
        assertEquals(AiProvider.CUSTOM, AiProvider.fromId("custom"))
        assertEquals(AiProvider.OPENAI, AiProvider.fromId(null))
        assertEquals(AiProvider.OPENAI, AiProvider.fromId("skynet"))
    }

    @Test
    fun `AiProvider cloud entries require keys and local custom does not`() {
        assertTrue(AiProvider.OPENAI.needsKey)
        assertTrue(AiProvider.GEMINI.needsKey)
        assertTrue(AiProvider.ANTHROPIC.needsKey)
        assertTrue(!AiProvider.CUSTOM.needsKey)
        assertTrue(AiProvider.CUSTOM.showBaseUrl)
        assertTrue(!AiProvider.OPENAI.showBaseUrl)
        assertTrue(AiProvider.entries.map { it.id }.distinct().size == AiProvider.entries.size)
    }

    @Test
    fun `AiProvider defaults point at cheap current models`() {
        // Cheap/free picks per provider: OpenAI has no free tier (cheapest mini),
        // Gemini + local run free, Anthropic is paid-only (cheapest Haiku).
        assertEquals("gpt-4o-mini", AiProvider.OPENAI.defaultModel)
        assertEquals("gemini-3.1-flash-lite", AiProvider.GEMINI.defaultModel)
        assertEquals("claude-haiku-4-5", AiProvider.ANTHROPIC.defaultModel)
        assertEquals("llama3.2", AiProvider.CUSTOM.defaultModel)
        for (provider in AiProvider.entries) {
            assertTrue(provider.defaultModel.isNotBlank())
        }
    }

    @Test
    fun `extractJsonObject strips markdown fences`() {
        val fenced = "```json\n{\"a\": 1}\n```"
        assertEquals("{\"a\": 1}", AiService.extractJsonObject(fenced))
    }

    @Test
    fun `extractJsonObject trims surrounding prose`() {
        assertEquals(
            "{\"errorCode\": \"P0301\"}",
            AiService.extractJsonObject("Here you go: {\"errorCode\": \"P0301\"} hope it helps")
        )
    }

    @Test
    fun `extractJsonObject passes plain text through`() {
        assertEquals("not json at all", AiService.extractJsonObject("not json at all"))
    }

    @Test
    fun `parseErrorInfo tolerates fenced JSON from chatty models`() {
        val dto = AiService.parseErrorInfo(
            "```json\n{\"errorCode\": \"P0301\", \"severity\": 2, \"title\": \"t\", " +
                "\"detail\": \"d\", \"implications\": \"i\", \"suggestedActions\": []}\n```"
        )
        assertEquals("P0301", dto.errorCode)
        assertEquals(ErrorSeverity.HIGH, dto.severity)
    }

    @Test
    fun `parseOpenAiCompatibleResponse extracts message content`() {
        val json = """{"choices": [{"message": {"content": "{\"a\":1}"}}]}"""
        assertEquals("{\"a\":1}", AiService.parseOpenAiCompatibleResponse(json))
    }

    @Test
    fun `parseOpenAiCompatibleResponse falls back on malformed envelopes`() {
        assertEquals("{}", AiService.parseOpenAiCompatibleResponse("{}"))
    }

    @Test
    fun `parseGeminiResponse extracts candidate text`() {
        val json = """{"candidates": [{"content": {"parts": [{"text": "{\"a\":1}"}]}}]}"""
        assertEquals("{\"a\":1}", AiService.parseGeminiResponse(json))
    }

    @Test
    fun `parseGeminiResponse falls back on malformed envelopes`() {
        assertEquals("{}", AiService.parseGeminiResponse("{\"error\": {}}"))
    }

    @Test
    fun `parseAnthropicResponse extracts block text`() {
        val json = """{"content": [{"type": "text", "text": "{\"a\":1}"}]}"""
        assertEquals("{\"a\":1}", AiService.parseAnthropicResponse(json))
    }

    @Test
    fun `parseAnthropicResponse falls back on malformed envelopes`() {
        assertEquals("{}", AiService.parseAnthropicResponse("[]"))
    }

    @Test
    fun `buildOfflineAssessment decodes powertrain generic code`() {
        val dto = AiService.buildOfflineAssessment("P0301")

        assertEquals("P0301", dto.errorCode)
        assertTrue(dto.offline)
        assertTrue(dto.title.contains("P0301"))
        assertTrue(dto.title.contains("Powertrain"))
        assertTrue(dto.detail.contains("Generic"))
        assertTrue(dto.suggestedActions.isNotEmpty())
    }

    @Test
    fun `buildOfflineAssessment decodes chassis body and network systems`() {
        assertTrue(AiService.buildOfflineAssessment("C1234").title.contains("Chassis"))
        assertTrue(AiService.buildOfflineAssessment("B0049").title.contains("Body"))
        assertTrue(AiService.buildOfflineAssessment("U0100").title.contains("Network"))
    }

    @Test
    fun `buildOfflineAssessment flags manufacturer-specific codes`() {
        assertTrue(AiService.buildOfflineAssessment("P1ABC").detail.contains("Manufacturer-specific"))
    }

    @Test
    fun `buildOfflineAssessment normalizes case and never throws`() {
        val dto = AiService.buildOfflineAssessment("  p0301 ")
        assertEquals("P0301", dto.errorCode)

        val unknown = AiService.buildOfflineAssessment("???")
        assertEquals("???", unknown.errorCode)
        assertTrue(unknown.offline)
    }

    @Test
    fun `offline assessment prefers the dictionary entry`() {
        val info = DtcInfo("P0420", "Weak catalyst", "Detail.", "Fix soon.", listOf("Check exhaust"))
        val dto = AiService.buildOfflineAssessment("P0420", info)
        assertTrue(dto.offline)
        assertEquals("Weak catalyst", dto.title)
        assertEquals(listOf("Check exhaust"), dto.suggestedActions)
    }

    @Test
    fun `offline assessment stays generic without a dictionary entry`() {
        val dto = AiService.buildOfflineAssessment("P0420", null)
        assertTrue(dto.offline)
        assertTrue(dto.detail.contains("Add an AI API key"))
    }

    @Test
    fun `sanitizeSpoken strips markdown and caps length`() {
        assertEquals(
            "hello world",
            AiService.sanitizeSpoken("**hello** `world`")
        )
        val long = AiService.sanitizeSpoken("x ".repeat(500), maxChars = 100)
        assertTrue(long.length <= 101)
    }

    @Test
    fun `follow-up system prompt carries the diagnosis and vehicle state`() {
        val dto = DtpCodeDTO(
            "P0301", ErrorSeverity.HIGH, "Cylinder 1 misfire",
            "Detail text.", "May damage catalyst.", listOf("Check plugs")
        )
        val system = AiService.buildFollowUpSystemPrompt(
            dto, MilStatus(milOn = true, storedCount = 2), "1M8GDM9AXKP042788"
        )
        assertTrue(system.contains("P0301"))
        assertTrue(system.contains("Cylinder 1 misfire"))
        assertTrue(system.contains("HIGH"))
        assertTrue(system.contains("1M8GDM9AXKP042788"))
        assertTrue(system.contains("mechanic"))
    }

    @Test
    fun `follow-up system prompt marks unknown lamp and missing vin`() {
        val dto = AiService.buildOfflineAssessment("P0420")
        val system = AiService.buildFollowUpSystemPrompt(dto, null, null)
        assertTrue(system.contains("unknown"))
        assertTrue(!system.contains("VIN"))
    }

    @Test
    fun `follow-up user text asks the question with capped history`() {
        val dto = AiService.buildOfflineAssessment("P0301")
        val history = (1..5).map { "Q$it" to "A$it" }
        val user = AiService.buildFollowUpUserText(dto, history, "Can I still drive?")
        assertTrue(user.contains("Can I still drive?"))
        assertTrue(user.contains("P0301"))
        assertTrue(!user.contains("Q1"))
        assertTrue(!user.contains("Q2"))
        assertTrue(user.contains("Q5"))
        val fresh = AiService.buildFollowUpUserText(dto, emptyList(), "What first?")
        assertTrue(fresh.contains("What first?"))
    }
}
