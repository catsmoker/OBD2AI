package com.catsmoker.obd2ai

import com.github.eltonvs.obd.command.ObdRawResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure parsing/formatting logic in AppCore.kt.
 * These functions have no Android dependencies, so they run on the JVM.
 */
class AppCoreTest {

    // ------------------------------------------------------------------
    // OBD hex command handlers
    // ------------------------------------------------------------------

    @Test
    fun `speed command parses classic ELM response`() {
        val response = ObdRawResponse("410D1F\r\r>", elapsedTime = 0L)
        assertEquals("31", MySpeedCommand().handler(response))
    }

    @Test
    fun `speed command handles response with echo`() {
        // Some adapters echo the request before the answer; processedValue strips
        // whitespace, so "010D\r410D00" contains the identifier for parsing.
        val response = ObdRawResponse("010D\r410D00\r\r>", elapsedTime = 0L)
        assertEquals("0", MySpeedCommand().handler(response))
    }

    @Test
    fun `speed command returns blank when identifier missing`() {
        val response = ObdRawResponse("NO DATA\r\r>", elapsedTime = 0L)
        assertEquals("", MySpeedCommand().handler(response))
    }

    @Test
    fun `speed command returns blank on non-hex payload`() {
        val response = ObdRawResponse("410DZZ\r\r>", elapsedTime = 0L)
        assertEquals("", MySpeedCommand().handler(response))
    }

    @Test
    fun `rpm command parses two-byte value`() {
        // (0x1A * 256 + 0xF8) / 4 = 6904 / 4 = 1726 RPM
        val response = ObdRawResponse("410C1AF8\r\r>", elapsedTime = 0L)
        assertEquals("1726", MyRPMCommand().handler(response))
    }

    @Test
    fun `rpm command returns blank when truncated`() {
        val response = ObdRawResponse("410C1A\r\r>", elapsedTime = 0L)
        assertEquals("", MyRPMCommand().handler(response))
    }

    @Test
    fun `rpm command returns blank on non-hex payload`() {
        val response = ObdRawResponse("410CZZZZ\r\r>", elapsedTime = 0L)
        assertEquals("", MyRPMCommand().handler(response))
    }

    @Test
    fun `coolant temperature command subtracts 40 offset`() {
        // 0x5A = 90 -> 90 - 40 = 50 °C
        val response = ObdRawResponse("41055A\r\r>", elapsedTime = 0L)
        assertEquals("50", MyCoolantTempCommand().handler(response))
    }

    @Test
    fun `coolant temperature command returns blank when missing`() {
        val response = ObdRawResponse("4105\r\r>", elapsedTime = 0L)
        assertEquals("", MyCoolantTempCommand().handler(response))
    }

    @Test
    fun `coolant temperature command returns blank on non-hex payload`() {
        val response = ObdRawResponse("4105ZZ\r\r>", elapsedTime = 0L)
        assertEquals("", MyCoolantTempCommand().handler(response))
    }

    // ------------------------------------------------------------------
    // Extended PID command handlers (throttle, intake temp, MAF, timing, trims)
    // ------------------------------------------------------------------

    @Test
    fun `throttle command parses single byte percent`() {
        // 0x82 = 130 -> 130 * 100 / 255 = 50 (integer division)
        val response = ObdRawResponse("411182\r\r>", elapsedTime = 0L)
        assertEquals("50", MyThrottleCommand().handler(response))
    }

    @Test
    fun `throttle command returns blank on garbage`() {
        assertEquals("", MyThrottleCommand().handler(ObdRawResponse("NO DATA\r\r>", elapsedTime = 0L)))
        assertEquals("", MyThrottleCommand().handler(ObdRawResponse("4111ZZ\r\r>", elapsedTime = 0L)))
    }

    @Test
    fun `intake temp command subtracts 40 offset`() {
        // 0x46 = 70 -> 30 °C
        val response = ObdRawResponse("410F46\r\r>", elapsedTime = 0L)
        assertEquals("30", MyIntakeTempCommand().handler(response))
    }

    @Test
    fun `intake temp command returns blank on garbage`() {
        assertEquals("", MyIntakeTempCommand().handler(ObdRawResponse("410F\r\r>", elapsedTime = 0L)))
    }

    @Test
    fun `maf command parses two byte grams per second`() {
        // (0x01 * 256 + 0xF4) / 100 = 500 / 100 = 5.0 g/s
        val response = ObdRawResponse("411001F4\r\r>", elapsedTime = 0L)
        assertEquals("5.0", MyMafCommand().handler(response))
    }

    @Test
    fun `maf command returns blank when truncated or garbage`() {
        assertEquals("", MyMafCommand().handler(ObdRawResponse("411001\r\r>", elapsedTime = 0L)))
        assertEquals("", MyMafCommand().handler(ObdRawResponse("4110ZZZZ\r\r>", elapsedTime = 0L)))
    }

    @Test
    fun `timing command handles negative advance`() {
        // 0x40 = 64 -> 64 / 2 - 64 = -32.0 (retarded timing is valid)
        val response = ObdRawResponse("410E40\r\r>", elapsedTime = 0L)
        assertEquals("-32.0", MyTimingCommand().handler(response))
    }

    @Test
    fun `timing command parses positive advance`() {
        // 0xA0 = 160 -> 80 - 64 = 16.0
        val response = ObdRawResponse("410EA0\r\r>", elapsedTime = 0L)
        assertEquals("16.0", MyTimingCommand().handler(response))
    }

    @Test
    fun `short fuel trim command centers 128 at zero`() {
        // 0x80 = 128 -> 0.0 %; 0x90 = 144 -> +12.5 %
        assertEquals("0.0", MyShortFuelTrimCommand().handler(ObdRawResponse("410680\r\r>", elapsedTime = 0L)))
        assertEquals("12.5", MyShortFuelTrimCommand().handler(ObdRawResponse("410690\r\r>", elapsedTime = 0L)))
    }

    @Test
    fun `long fuel trim command handles negative trim`() {
        // 0x70 = 112 -> (112 - 128) * 100 / 128 = -12.5 %
        val response = ObdRawResponse("410870\r\r>", elapsedTime = 0L)
        assertEquals("-12.5", MyLongFuelTrimCommand().handler(response))
    }

    @Test
    fun `fuel trim commands return blank on garbage`() {
        assertEquals("", MyShortFuelTrimCommand().handler(ObdRawResponse("NO DATA\r\r>", elapsedTime = 0L)))
        assertEquals("", MyLongFuelTrimCommand().handler(ObdRawResponse("4108ZZ\r\r>", elapsedTime = 0L)))
    }

    // ------------------------------------------------------------------
    // PID registry + support bitmask discovery
    // ------------------------------------------------------------------

    @Test
    fun `pid registry has unique pids`() {
        val pids = PidRegistry.all.map { it.pid }
        assertEquals(pids.size, pids.toSet().size)
    }

    @Test
    fun `pid registry covers the polled and discovered pids`() {
        for (pid in listOf(0x04, 0x05, 0x06, 0x08, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x2F, 0x42)) {
            assertTrue("missing PID ${pid.toString(16)}", PidRegistry.forPid(pid) != null)
        }
    }

    @Test
    fun `support bitmask decodes classic 0100 response`() {
        // BE1FA813: PIDs 01,03-07,0C-10,11,13,15,1C,1F,20 supported.
        val supported = PidRegistry.parseSupportBitmask("BE1FA813", 0x00)
        assertTrue(supported.contains(0x01))
        assertTrue(supported.contains(0x0C))
        assertTrue(supported.contains(0x0D))
        assertTrue(supported.contains(0x11))
        assertTrue(supported.contains(0x20))
        assertTrue(!supported.contains(0x02))
        assertTrue(!supported.contains(0x08))
    }

    @Test
    fun `support bitmask offsets ranges correctly`() {
        // 0x0120 range: same payload shape means 0x21, 0x2C, 0x2D...
        val supported = PidRegistry.parseSupportBitmask("BE1FA813", 0x20)
        assertTrue(supported.contains(0x21))
        assertTrue(supported.contains(0x2C))
        assertTrue(supported.contains(0x2D))
        assertTrue(!supported.contains(0x0C))
    }

    @Test
    fun `support bitmask tolerates case whitespace and garbage`() {
        assertEquals(
            PidRegistry.parseSupportBitmask("BE1FA813", 0x00),
            PidRegistry.parseSupportBitmask("be 1f a8 13", 0x00),
        )
        assertTrue(PidRegistry.parseSupportBitmask("", 0x00).isEmpty())
        assertTrue(PidRegistry.parseSupportBitmask("NO DATA", 0x00).isEmpty())
        assertTrue(PidRegistry.parseSupportBitmask("ZZ", 0x00).isEmpty())
    }

    @Test
    fun `support probe command extracts payload after identifier`() {
        val response = ObdRawResponse("4100BE1FA813\r\r>", elapsedTime = 0L)
        assertEquals("BE1FA813", MySupportedPidsCommand("00").handler(response))
    }

    @Test
    fun `support probe command returns blank without identifier`() {
        assertEquals("", MySupportedPidsCommand("00").handler(ObdRawResponse("NO DATA\r\r>", elapsedTime = 0L)))
    }

    @Test
    fun `plausibility gate rejects out of range readings`() {
        assertTrue(PidRegistry.isPlausible(0x11, 50.0))
        assertTrue(!PidRegistry.isPlausible(0x11, 101.0))
        assertTrue(!PidRegistry.isPlausible(0x05, 300.0))
        assertTrue(PidRegistry.isPlausible(0x0E, -32.0))
        // Unknown PIDs pass through (no definition to judge by).
        assertTrue(PidRegistry.isPlausible(0x99, 12345.0))
    }

    @Test
    fun `unknown support state counts as supported`() {
        ObdDataHolder.supportedPids = null
        assertTrue(ObdDataHolder.isPidSupported(0x0C))
        ObdDataHolder.supportedPids = setOf(0x0C)
        assertTrue(ObdDataHolder.isPidSupported(0x0C))
        assertTrue(!ObdDataHolder.isPidSupported(0x11))
        ObdDataHolder.supportedPids = null
    }

    @Test
    fun `demo waves stay in plausible ranges and correlate`() {
        for (second in 0 until 120 step 7) {
            val throttle = DemoObdSource.throttleAt(second)
            assertTrue("throttle $throttle", throttle in 0..100)
            val intake = DemoObdSource.intakeTempAt(second)
            assertTrue("iat $intake", PidRegistry.isPlausible(0x0F, intake.toDouble()))
            val maf = DemoObdSource.mafAt(second)
            assertTrue("maf $maf", PidRegistry.isPlausible(0x10, maf))
            val timing = DemoObdSource.timingAt(second)
            assertTrue("timing $timing", PidRegistry.isPlausible(0x0E, timing))
            val stft = DemoObdSource.shortTrimAt(second)
            assertTrue("stft $stft", stft in -15.0..15.0)
            val ltft = DemoObdSource.longTrimAt(second)
            assertTrue("ltft $ltft", ltft in -15.0..15.0)
        }
    }

    // ------------------------------------------------------------------
    // DTC list splitting
    // ------------------------------------------------------------------

    @Test
    fun `splitErrors returns empty list for NO DATA`() {
        assertTrue(ObdHelper.splitErrors("NO DATA").isEmpty())
    }

    @Test
    fun `splitErrors is case-insensitive for NO DATA`() {
        assertTrue(ObdHelper.splitErrors("no data").isEmpty())
    }

    @Test
    fun `splitErrors returns empty list for blank input`() {
        assertTrue(ObdHelper.splitErrors("   ").isEmpty())
    }

    @Test
    fun `splitErrors splits on whitespace and commas`() {
        assertEquals(
            listOf("P0301", "P0302", "P0455"),
            ObdHelper.splitErrors("P0301 P0302,P0455")
        )
    }

    @Test
    fun `splitErrors trims stray whitespace around tokens`() {
        assertEquals(
            listOf("P0301", "P0442"),
            ObdHelper.splitErrors(" P0301 , P0442 ")
        )
    }

    @Test
    fun `splitErrors drops ELM327 status chatter`() {
        assertTrue(ObdHelper.splitErrors("NODATA").isEmpty())
        assertTrue(ObdHelper.splitErrors("?").isEmpty())
        assertTrue(ObdHelper.splitErrors("SEARCHING... NO DATA").isEmpty())
        assertTrue(ObdHelper.splitErrors("STOPPED").isEmpty())
        assertTrue(ObdHelper.splitErrors("UNABLE TO CONNECT").isEmpty())
        assertEquals(
            listOf("P0301"),
            ObdHelper.splitErrors("P0301 STOPPED")
        )
        assertEquals(
            listOf("P0301", "P0455"),
            ObdHelper.splitErrors("SEARCHING...\rP0301,P0455\r>")
        )
    }

    // ------------------------------------------------------------------
    // OpenAI JSON parsing
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // ErrorSeverity mapping
    // ------------------------------------------------------------------

    @Test
    fun `ErrorSeverity fromInt maps known values and rejects others`() {
        assertEquals(ErrorSeverity.LOW, ErrorSeverity.fromInt(0))
        assertEquals(ErrorSeverity.MEDIUM, ErrorSeverity.fromInt(1))
        assertEquals(ErrorSeverity.HIGH, ErrorSeverity.fromInt(2))
        var threw = false
        try {
            ErrorSeverity.fromInt(9)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    // ------------------------------------------------------------------
    // MIL status parsing
    // ------------------------------------------------------------------

    @Test
    fun `parseMilStatus maps lamp state and stored count`() {
        assertEquals(MilStatus(true, 3), ObdHelper.parseMilStatus("true", "3"))
        assertEquals(MilStatus(false, 0), ObdHelper.parseMilStatus("false", "0"))
    }

    @Test
    fun `parseMilStatus is defensive about garbage input`() {
        assertEquals(MilStatus(false, 0), ObdHelper.parseMilStatus("maybe", "many"))
        assertEquals(MilStatus(false, 0), ObdHelper.parseMilStatus("false", "-2"))
    }

    // ------------------------------------------------------------------
    // AI provider mapping
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // AI response parsing
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // Offline DTC assessment fallback
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // DTC result cache
    // ------------------------------------------------------------------

    @Test
    fun `DtcStore round-trips assessed results`() {
        val results = listOf(
            DtpCodeDTO("P0301", ErrorSeverity.HIGH, "Misfire", "detail", "impl", listOf("a", "b"), offline = false),
            AiService.buildOfflineAssessment("C1234")
        )

        val restored = DtcStore.fromJson(DtcStore.toJson(results))

        assertEquals(results, restored)
    }

    @Test
    fun `DtcStore returns empty list for corrupt cache`() {
        assertTrue(DtcStore.fromJson("not json at all").isEmpty())
        assertTrue(DtcStore.fromJson("[]").isEmpty())
    }

    @Test
    fun `DtcStore skips entries without a code`() {
        val json = """[{"title": "no code here"}]"""
        assertTrue(DtcStore.fromJson(json).isEmpty())
    }

    // ------------------------------------------------------------------
    // Theme mode preference
    // ------------------------------------------------------------------

    @Test
    fun `ThemeMode fromPref maps known values and defaults to system`() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromPref("light"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromPref("dark"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPref("system"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPref(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPref("amoled"))
    }

    @Test
    fun `ThemeMode migrates the legacy dark-mode switch`() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromLegacyDarkMode(true))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromLegacyDarkMode(false))
    }

    // ------------------------------------------------------------------
    // App language preference
    // ------------------------------------------------------------------

    @Test
    fun `AppLanguage fromTag maps known tags and defaults to system`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"))
        assertEquals(AppLanguage.SPANISH, AppLanguage.fromTag("es"))
        assertEquals(AppLanguage.ARABIC, AppLanguage.fromTag("ar"))
        assertEquals(AppLanguage.CHINESE, AppLanguage.fromTag("zh-CN"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("system"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("fr"))
    }

    @Test
    fun `AppLanguage tags are unique`() {
        val tags = AppLanguage.entries.map { it.tag }
        assertEquals(tags.size, tags.distinct().size)
    }

    // ------------------------------------------------------------------
    // Locale parity: every translated file must mirror the default keys
    // ------------------------------------------------------------------

    @Test
    fun `all locales mirror the default string keys`() {
        fun keysOf(path: String): Set<String> {
            val file = java.io.File(path)
            assertTrue("missing resource file: $path", file.exists())
            val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(file)
            val nodes = doc.getElementsByTagName("string")
            return (0 until nodes.length).mapNotNull { i ->
                val el = nodes.item(i) as org.w3c.dom.Element
                if (el.getAttribute("translatable") == "false") null
                else el.getAttribute("name")
            }.toSet()
        }

        val base = keysOf("src/main/res/values/strings.xml")
        assertTrue(base.isNotEmpty())
        for (locale in listOf("values-es", "values-ar", "values-zh-rCN")) {
            val keys = keysOf("src/main/res/$locale/strings.xml")
            assertTrue("$locale missing keys: ${base - keys}", (base - keys).isEmpty())
            assertTrue("$locale extra keys: ${keys - base}", (keys - base).isEmpty())
        }
    }

    // ------------------------------------------------------------------
    // Layout ID parity: land/tablet variants must keep every default ID,
    // or findViewById returns null and the fragment crashes on rotation.
    // ------------------------------------------------------------------

    @Test
    fun `land and tablet layout variants keep every default view ID`() {
        fun idsOf(path: String): Set<String> {
            val file = java.io.File(path)
            assertTrue("missing layout file: $path", file.exists())
            return Regex("""@\+id/([A-Za-z0-9_]+)""")
                .findAll(file.readText()).map { it.groupValues[1] }.toSet()
        }

        val pairs = listOf(
            "layout/fragment_live_data.xml" to "layout-land/fragment_live_data.xml",
            "layout/fragment_error_overview.xml" to "layout-land/fragment_error_overview.xml",
            "layout/fragment_error_overview.xml" to "layout-sw600dp/fragment_error_overview.xml",
            "layout/fragment_connect.xml" to "layout-land/fragment_connect.xml"
        )
        for ((default, variant) in pairs) {
            val defaultIds = idsOf("src/main/res/$default")
            val variantIds = idsOf("src/main/res/$variant")
            val missing = defaultIds - variantIds
            assertTrue("$variant missing IDs: $missing", missing.isEmpty())
        }
    }

    // ------------------------------------------------------------------
    // RPM warning tiers
    // ------------------------------------------------------------------

    @Test
    fun `nextRpmTier escalates through the bands`() {
        assertEquals(1, LiveDataFragment.nextRpmTier(1500, 0))
        assertEquals(2, LiveDataFragment.nextRpmTier(2500, 1))
        assertEquals(3, LiveDataFragment.nextRpmTier(3500, 2))
        assertEquals(4, LiveDataFragment.nextRpmTier(4600, 3))
    }

    @Test
    fun `nextRpmTier jumps straight to danger on a spike`() {
        assertEquals(4, LiveDataFragment.nextRpmTier(5000, 0))
    }

    @Test
    fun `nextRpmTier holds while hovering inside a band`() {
        assertEquals(4, LiveDataFragment.nextRpmTier(4450, 4))
        assertEquals(0, LiveDataFragment.nextRpmTier(950, 0))
        assertEquals(4, LiveDataFragment.nextRpmTier(4500, 4))
    }

    @Test
    fun `nextRpmTier steps down through hysteresis gaps`() {
        // Danger releases below 4400, not at the 4500 trigger edge.
        assertEquals(4, LiveDataFragment.nextRpmTier(4450, 4))
        assertEquals(3, LiveDataFragment.nextRpmTier(4300, 4))
        assertEquals(2, LiveDataFragment.nextRpmTier(2500, 3))
        assertEquals(1, LiveDataFragment.nextRpmTier(1500, 2))
        assertEquals(0, LiveDataFragment.nextRpmTier(500, 1))
    }

    @Test
    fun `nextRpmTier drops straight to zero from a stall`() {
        assertEquals(0, LiveDataFragment.nextRpmTier(500, 4))
    }

    @Test
    fun `rpmZoneColor paints green amber red bands`() {
        val green = LiveDataFragment.rpmZoneColor(0)
        assertEquals(green, LiveDataFragment.rpmZoneColor(2499))
        val amber = LiveDataFragment.rpmZoneColor(2500)
        assertEquals(amber, LiveDataFragment.rpmZoneColor(3499))
        val red = LiveDataFragment.rpmZoneColor(3500)
        assertEquals(red, LiveDataFragment.rpmZoneColor(6000))
        assertTrue(green != amber)
        assertTrue(amber != red)
    }

    @Test
    fun `rpmSections are contiguous and cover the whole dial`() {
        // The gauge library throws when a section conflicts with its neighbour,
        // so the zones must tile [0, 1] with no gaps or overlaps.
        val sections = LiveDataFragment.rpmSections(60f)
        assertEquals(3, sections.size)
        assertEquals(0f, sections.first().first, 0f)
        assertEquals(1f, sections.last().second, 0f)
        for (i in sections.indices) {
            val (start, end, _) = sections[i]
            assertTrue("section $i inverted: $start..$end", start < end)
            assertTrue("section $i out of range", start >= 0f && end <= 1f)
            if (i > 0) {
                assertEquals("gap/overlap at $i", sections[i - 1].second, start, 0f)
            }
        }
    }

    @Test
    fun `rpmSections agree with rpmZoneColor`() {
        val sections = LiveDataFragment.rpmSections(60f)
        fun colorAt(rpm: Int): Int {
            val offset = rpm / 6000f
            return sections.first { offset >= it.first && offset < it.second || offset == 1f && it.second == 1f }.third
        }
        assertEquals(LiveDataFragment.rpmZoneColor(1500), colorAt(1500))
        assertEquals(LiveDataFragment.rpmZoneColor(3000), colorAt(3000))
        assertEquals(LiveDataFragment.rpmZoneColor(5000), colorAt(5000))
    }

    // ------------------------------------------------------------------
    // Offline AI cue triggers
    // ------------------------------------------------------------------

    @Test
    fun `shiftCrossed fires only on the rising edge`() {
        assertTrue(LiveDataFragment.shiftCrossed(3400, 3500, 3500))
        assertTrue(!LiveDataFragment.shiftCrossed(3500, 3600, 3500))
        assertTrue(!LiveDataFragment.shiftCrossed(3400, 3400, 3500))
        assertTrue(!LiveDataFragment.shiftCrossed(3600, 3400, 3500))
        assertTrue(!LiveDataFragment.shiftCrossed(1000, 2000, 3500))
    }

    @Test
    fun `crossedDown fires only on the falling edge`() {
        assertTrue(LiveDataFragment.crossedDown(800, 0, 400))
        assertTrue(!LiveDataFragment.crossedDown(300, 0, 400))
        assertTrue(!LiveDataFragment.crossedDown(800, 500, 400))
        assertTrue(!LiveDataFragment.crossedDown(0, 800, 400))
        assertTrue(!LiveDataFragment.crossedDown(400, 400, 400))
    }

    @Test
    fun `resolveAiSwitches keeps the assistants exclusive`() {
        // Turning one ON kills the other.
        assertEquals(
            Pair(true, false),
            LiveDataFragment.resolveAiSwitches(LiveDataFragment.AiSystem.OFFLINE, true, false, true)
        )
        assertEquals(
            Pair(false, true),
            LiveDataFragment.resolveAiSwitches(LiveDataFragment.AiSystem.ONLINE, true, true, false)
        )
        // Turning one OFF leaves the other unchanged; both may rest OFF.
        assertEquals(
            Pair(false, true),
            LiveDataFragment.resolveAiSwitches(LiveDataFragment.AiSystem.OFFLINE, false, true, true)
        )
        assertEquals(
            Pair(false, false),
            LiveDataFragment.resolveAiSwitches(LiveDataFragment.AiSystem.OFFLINE, false, true, false)
        )
        assertEquals(
            Pair(true, false),
            LiveDataFragment.resolveAiSwitches(LiveDataFragment.AiSystem.ONLINE, false, true, true)
        )
        assertEquals(
            Pair(false, false),
            LiveDataFragment.resolveAiSwitches(LiveDataFragment.AiSystem.ONLINE, false, false, true)
        )
    }

    @Test
    fun `enforceOnlineOutput always keeps one output`() {
        assertEquals(Pair(true, true), LiveDataFragment.enforceOnlineOutput(true, true, true))
        assertEquals(Pair(false, true), LiveDataFragment.enforceOnlineOutput(true, false, true))
        assertEquals(Pair(true, false), LiveDataFragment.enforceOnlineOutput(false, true, false))
        // Killing the last output revives the other one instead.
        assertEquals(Pair(false, true), LiveDataFragment.enforceOnlineOutput(true, false, false))
        assertEquals(Pair(true, false), LiveDataFragment.enforceOnlineOutput(false, false, false))
    }

    @Test
    fun `enforceOfflineOutput always keeps one output`() {
        assertEquals(Pair(true, true), LiveDataFragment.enforceOfflineOutput(true, true, true))
        assertEquals(Pair(false, true), LiveDataFragment.enforceOfflineOutput(true, false, true))
        assertEquals(Pair(true, false), LiveDataFragment.enforceOfflineOutput(false, true, false))
        assertEquals(Pair(false, true), LiveDataFragment.enforceOfflineOutput(true, false, false))
        assertEquals(Pair(true, false), LiveDataFragment.enforceOfflineOutput(false, false, false))
    }

    @Test
    fun `offlineAiReady enforces the cooldown`() {
        assertTrue(LiveDataFragment.offlineAiReady(0L, 15_000L, 15_000L))
        assertTrue(!LiveDataFragment.offlineAiReady(1000L, 15_000L, 15_000L))
        assertTrue(LiveDataFragment.offlineAiReady(1000L, 16_000L, 15_000L))
        assertTrue(LiveDataFragment.offlineAiReady(0L, 0L, 0L))
    }

    @Test
    fun `bannerDurationMs gives one second per word`() {        assertEquals(3_000L, LiveDataFragment.bannerDurationMs("Shift up now."))
        assertEquals(
            9_000L,
            LiveDataFragment.bannerDurationMs("Coolant overheat stop safely and check cooling now please")
        )
        assertEquals(2_000L, LiveDataFragment.bannerDurationMs(""))
        assertEquals(2_000L, LiveDataFragment.bannerDurationMs("   "))
        assertEquals(2_000L, LiveDataFragment.bannerDurationMs("Go"))
    }

    // ------------------------------------------------------------------
    // Easter eggs (Offline AI only)
    // ------------------------------------------------------------------

    @Test
    fun `speedEasterTier splits 130 140 150`() {
        assertEquals(0, LiveDataFragment.speedEasterTier(0f))
        assertEquals(0, LiveDataFragment.speedEasterTier(129.9f))
        assertEquals(1, LiveDataFragment.speedEasterTier(130f))
        assertEquals(1, LiveDataFragment.speedEasterTier(139.9f))
        assertEquals(2, LiveDataFragment.speedEasterTier(140f))
        assertEquals(2, LiveDataFragment.speedEasterTier(149.9f))
        assertEquals(3, LiveDataFragment.speedEasterTier(150f))
        assertEquals(3, LiveDataFragment.speedEasterTier(220f))
        assertEquals(LiveDataFragment.POOL_SPEED_130, LiveDataFragment.poolForSpeedTier(1))
        assertEquals(LiveDataFragment.POOL_SPEED_140, LiveDataFragment.poolForSpeedTier(2))
        assertEquals(LiveDataFragment.POOL_SPEED_150, LiveDataFragment.poolForSpeedTier(3))
        assertEquals(LiveDataFragment.POOL_SPEED_130, LiveDataFragment.poolForSpeedTier(0))
    }

    @Test
    fun `rollEasterEgg respects probability edges`() {
        assertTrue(LiveDataFragment.rollEasterEgg(0f, 0.5f))
        assertTrue(!LiveDataFragment.rollEasterEgg(0.5f, 0.5f))
        assertTrue(!LiveDataFragment.rollEasterEgg(0.99f, 0.5f))
        assertTrue(!LiveDataFragment.rollEasterEgg(0f, 0f))
        assertTrue(LiveDataFragment.rollEasterEgg(0.999f, 1f))
        assertTrue(!LiveDataFragment.rollEasterEgg(-0.1f, 0.5f))
    }

    @Test
    fun `pickFreshLine skips recent lines and rotates`() {
        val pool = listOf("a", "b", "c")
        val recent = ArrayDeque(listOf("a", "b"))
        assertEquals("c", LiveDataFragment.pickFreshLine(pool, recent, 0))
        assertEquals("c", LiveDataFragment.pickFreshLine(pool, recent, 1))
        assertEquals("a", LiveDataFragment.pickFreshLine(pool, ArrayDeque(), 0))
        assertEquals(null, LiveDataFragment.pickFreshLine(pool, ArrayDeque(listOf("a", "b", "c")), 0))
        assertEquals(null, LiveDataFragment.pickFreshLine(emptyList(), ArrayDeque(), 0))
    }

    @Test
    fun `comboActive needs both thresholds at once`() {
        assertTrue(LiveDataFragment.comboActive(5600, 5500, 125f, 120))
        assertTrue(!LiveDataFragment.comboActive(5400, 5500, 125f, 120))
        assertTrue(!LiveDataFragment.comboActive(5600, 5500, 110f, 120))
        assertTrue(!LiveDataFragment.comboActive(0, 5500, 0f, 120))
    }

    @Test
    fun `easterPoolConfig keeps ultra rare and unknown pools silent`() {
        val ultra = LiveDataFragment.easterPoolConfig(LiveDataFragment.POOL_ULTRA)
        assertTrue(ultra.probability in 0f..0.05f)
        assertTrue(ultra.cooldownMs >= 60 * 60_000L)
        for (pool in listOf(
            LiveDataFragment.POOL_SPEED_130,
            LiveDataFragment.POOL_SPEED_140,
            LiveDataFragment.POOL_SPEED_150,
            LiveDataFragment.POOL_RPM,
            LiveDataFragment.POOL_COMBO,
            LiveDataFragment.POOL_COOLANT_AFTER,
            LiveDataFragment.POOL_FAULT_NEW,
            LiveDataFragment.POOL_FAULT_RETURN,
            LiveDataFragment.POOL_FAULT_SPEEDING
        )) {
            val config = LiveDataFragment.easterPoolConfig(pool)
            assertTrue("$pool probability", config.probability in 0f..1f)
            assertTrue("$pool cooldown", config.cooldownMs > 0L)
            assertTrue("$pool delay", config.delayMs >= 0L)
        }
        assertEquals(0f, LiveDataFragment.easterPoolConfig("nope").probability)
    }

    // ------------------------------------------------------------------
    // Online AI event detector
    // ------------------------------------------------------------------

    @Test
    fun `dtcDiff reports new and cleared codes`() {
        val (new, cleared) = OnlineAiManager.dtcDiff(
            setOf("P0301", "P0455"), listOf("P0301", "P0420")
        )
        assertEquals(listOf("P0420"), new)
        assertEquals(listOf("P0455"), cleared)
    }

    @Test
    fun `dtcDiff is empty when nothing changed`() {
        val (new, cleared) = OnlineAiManager.dtcDiff(setOf("P0301"), listOf("P0301", "P0301"))
        assertTrue(new.isEmpty())
        assertTrue(cleared.isEmpty())
    }

    @Test
    fun `nextCoolantWatch latches with hysteresis`() {
        assertTrue(!OnlineAiManager.nextCoolantWatch(109, false))
        assertTrue(OnlineAiManager.nextCoolantWatch(110, false))
        assertTrue(OnlineAiManager.nextCoolantWatch(106, true))
        assertTrue(!OnlineAiManager.nextCoolantWatch(104, true))
        assertTrue(!OnlineAiManager.nextCoolantWatch(null, false))
    }

    @Test
    fun `voltage fuel and load thresholds`() {
        assertTrue(OnlineAiManager.voltageBad(11.9f))
        assertTrue(OnlineAiManager.voltageBad(15.1f))
        assertTrue(!OnlineAiManager.voltageBad(13.8f))
        assertTrue(!OnlineAiManager.voltageBad(null))
        assertTrue(OnlineAiManager.fuelLow(0))
        assertTrue(OnlineAiManager.fuelLow(15))
        assertTrue(!OnlineAiManager.fuelLow(16))
        assertTrue(!OnlineAiManager.fuelLow(null))
        assertTrue(OnlineAiManager.loadHigh(95))
        assertTrue(!OnlineAiManager.loadHigh(94))
        assertTrue(!OnlineAiManager.loadHigh(null))
    }

    @Test
    fun `allows gates severities by frequency`() {
        assertTrue(OnlineAiManager.allows(OnlineAiSeverity.FAULT, AiFrequency.LOW))
        assertTrue(!OnlineAiManager.allows(OnlineAiSeverity.WARNING, AiFrequency.LOW))
        assertTrue(!OnlineAiManager.allows(OnlineAiSeverity.INFO, AiFrequency.LOW))
        assertTrue(OnlineAiManager.allows(OnlineAiSeverity.WARNING, AiFrequency.NORMAL))
        assertTrue(!OnlineAiManager.allows(OnlineAiSeverity.INFO, AiFrequency.NORMAL))
        assertTrue(OnlineAiManager.allows(OnlineAiSeverity.INFO, AiFrequency.HIGH))
    }

    @Test
    fun `repeatCooldownMs stretches low and halves high`() {
        val normal = OnlineAiManager.repeatCooldownMs(OnlineAiSeverity.WARNING, AiFrequency.NORMAL)
        assertEquals(normal * 2, OnlineAiManager.repeatCooldownMs(OnlineAiSeverity.WARNING, AiFrequency.LOW))
        assertEquals(normal / 2, OnlineAiManager.repeatCooldownMs(OnlineAiSeverity.WARNING, AiFrequency.HIGH))
        assertTrue(normal >= 60_000L)
    }

    @Test
    fun `buildSystemPrompt carries personality and brevity rule`() {
        val funny = OnlineAiManager.buildSystemPrompt(AiPersonality.FUNNY)
        assertTrue(funny.contains("joke"))
        assertTrue(funny.contains("40 words"))
        val pro = OnlineAiManager.buildSystemPrompt(AiPersonality.PROFESSIONAL)
        assertTrue(pro.contains("No jokes"))
        assertTrue(AiPersonality.fromPref(null) == AiPersonality.NORMAL)
        assertTrue(AiFrequency.fromPref("bogus") == AiFrequency.NORMAL)
    }

    @Test
    fun `buildUserText includes every reading`() {
        val text = OnlineAiManager.buildUserText(
            "New fault code detected: P0301",
            OnlineAiSeverity.FAULT,
            OnlineAiSnapshot(82, 3100, 94, 68, 13.8f, 64, listOf("P0301")),
            ""
        )
        assertTrue(text.contains("P0301"))
        assertTrue(text.contains("3100"))
        assertTrue(text.contains("94"))
        assertTrue(text.contains("fault"))
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
    fun `parseVoltage finds the first decimal`() {
        assertEquals(13.8f, ObdHelper.parseVoltage("13.8 V")!!, 0.001f)
        assertEquals(12.6f, ObdHelper.parseVoltage("12.6V")!!, 0.001f)
        assertEquals(null, ObdHelper.parseVoltage("NO DATA"))
    }

    // ------------------------------------------------------------------
    // Online AI personality (brain-generated; Offline AI pools untouched)
    // ------------------------------------------------------------------

    @Test
    fun `personality occasions are sane and ultra stays rarest`() {
        val occasions = OnlineAiPersonality.OCCASIONS
        assertEquals(13, occasions.size)
        assertEquals(occasions.keys.size, occasions.values.map { it.key }.toSet().size)
        for ((key, occasion) in occasions) {
            assertEquals(key, occasion.key)
            assertTrue("$key examples", occasion.styleExamples.size in 2..12)
            assertTrue("$key lines short", occasion.styleExamples.all { it.length <= 140 })
            assertTrue("$key lines non-blank", occasion.styleExamples.all { it.isNotBlank() })
            assertTrue("$key probability", occasion.probability in 0f..1f)
            assertTrue("$key cooldown", occasion.cooldownMs >= 60_000L)
        }
        val ultra = occasions.getValue(OnlineAiPersonality.ULTRA)
        assertEquals(OnlineAiPersonality.Rarity.LEGENDARY, ultra.rarity)
        assertTrue(ultra.probability <= 0.05f)
        assertTrue(occasions.values.filter { it.key != ultra.key }.all { it.probability >= ultra.probability })
        assertTrue(occasions.values.filter { it.key != ultra.key }.all { it.cooldownMs <= ultra.cooldownMs })
    }

    @Test
    fun `personality examples stay clean and TTS-safe`() {
        val banned = listOf("damn", "hell", "shit", "fuck", "bitch", "asshole", "bastard", "dick")
        for ((key, occasion) in OnlineAiPersonality.OCCASIONS) {
            for (line in occasion.styleExamples) {
                val lower = " $line ".lowercase()
                assertTrue(
                    "$key has no profanity: $line",
                    banned.none { bad -> Regex("\\b$bad\\b").containsMatchIn(lower) }
                )
                assertTrue("$key has no percent: $line", '%' !in line)
            }
        }
    }

    @Test
    fun `personality rarity gates follow frequency`() {
        assertTrue(
            OnlineAiPersonality.allowsPersonality(
                AiFrequency.LOW, OnlineAiPersonality.Rarity.LEGENDARY
            )
        )
        assertTrue(
            !OnlineAiPersonality.allowsPersonality(AiFrequency.LOW, OnlineAiPersonality.Rarity.COMMON)
        )
        assertTrue(
            !OnlineAiPersonality.allowsPersonality(
                AiFrequency.NORMAL, OnlineAiPersonality.Rarity.COMMON
            )
        )
        assertTrue(
            OnlineAiPersonality.allowsPersonality(AiFrequency.NORMAL, OnlineAiPersonality.Rarity.UNCOMMON)
        )
        assertTrue(
            OnlineAiPersonality.allowsPersonality(AiFrequency.HIGH, OnlineAiPersonality.Rarity.COMMON)
        )
        val low = OnlineAiPersonality.globalCooldownMs(AiFrequency.LOW)
        val normal = OnlineAiPersonality.globalCooldownMs(AiFrequency.NORMAL)
        val high = OnlineAiPersonality.globalCooldownMs(AiFrequency.HIGH)
        assertTrue(low > normal)
        assertTrue(normal > high)
        assertTrue(high >= 60_000L)
    }

    @Test
    fun `personality speed tiers split 130 140 150`() {
        assertEquals(0, OnlineAiPersonality.speedTier(129))
        assertEquals(1, OnlineAiPersonality.speedTier(130))
        assertEquals(2, OnlineAiPersonality.speedTier(140))
        assertEquals(3, OnlineAiPersonality.speedTier(150))
        assertEquals(OnlineAiPersonality.SPEED_150, OnlineAiPersonality.poolForSpeedTier(3))
        assertEquals(OnlineAiPersonality.SPEED_140, OnlineAiPersonality.poolForSpeedTier(2))
        assertEquals(OnlineAiPersonality.SPEED_130, OnlineAiPersonality.poolForSpeedTier(1))
    }

    @Test
    fun `personality rpm spike needs a 1500 jump`() {
        assertTrue(OnlineAiPersonality.isRpmSpike(2000, 3500))
        assertTrue(!OnlineAiPersonality.isRpmSpike(2000, 3499))
        assertTrue(!OnlineAiPersonality.isRpmSpike(null, 6000))
        assertTrue(!OnlineAiPersonality.isRpmSpike(4000, 2000))
    }

    @Test
    fun `personality warm zone stays below the diagnostic watch`() {
        assertTrue(OnlineAiPersonality.enteredWarmZone(99, 100))
        assertTrue(OnlineAiPersonality.enteredWarmZone(90, 109))
        assertTrue(!OnlineAiPersonality.enteredWarmZone(100, 105))
        assertTrue(!OnlineAiPersonality.enteredWarmZone(90, 110))
        assertTrue(!OnlineAiPersonality.enteredWarmZone(90, 99))
        assertTrue(!OnlineAiPersonality.enteredWarmZone(null, 105))
    }

    @Test
    fun `personality combos need both sides at once`() {
        assertTrue(OnlineAiPersonality.comboSpeedRpm(130, 5000, 3500))
        assertTrue(!OnlineAiPersonality.comboSpeedRpm(129, 5000, 3500))
        assertTrue(!OnlineAiPersonality.comboSpeedRpm(130, 4999, 3500))
        assertTrue(OnlineAiPersonality.comboSpeedFault(120))
        assertTrue(!OnlineAiPersonality.comboSpeedFault(119))
        assertTrue(!OnlineAiPersonality.comboSpeedFault(null))
        assertTrue(OnlineAiPersonality.comboRpmCoolant(5000, 3500, 100))
        assertTrue(!OnlineAiPersonality.comboRpmCoolant(5000, 3500, 99))
        assertTrue(OnlineAiPersonality.calmHotRpm(5000, 3500, 90))
        assertTrue(!OnlineAiPersonality.calmHotRpm(5000, 3500, null))
        assertTrue(!OnlineAiPersonality.calmHotRpm(5000, 3500, 100))
    }

    @Test
    fun `personality prompt carries context examples and TTS rules`() {
        val occasion = OnlineAiPersonality.OCCASIONS.getValue(OnlineAiPersonality.FAULT_NEW)
        val system = OnlineAiPersonality.buildPersonalitySystem(AiPersonality.NORMAL)
        assertTrue(system.contains("25 words"))
        assertTrue(system.contains("never pretend to be human"))
        assertTrue(system.contains("No profanity"))
        assertTrue(system.contains("spell measurements out in words"))
        val funny = OnlineAiPersonality.buildPersonalitySystem(AiPersonality.FUNNY)
        assertTrue(funny.contains("joke"))
        val pro = OnlineAiPersonality.buildPersonalitySystem(AiPersonality.PROFESSIONAL)
        assertTrue(pro.contains("No jokes"))
        val user = OnlineAiPersonality.buildPersonalityUser(
            occasion, AiPersonality.NORMAL,
            137, 3100, 94, 68, listOf("P0300"), "New fault code detected: P0300."
        )
        assertTrue(user.contains("137"))
        assertTrue(user.contains("P0300"))
        assertTrue(user.contains("never copy one exactly"))
        assertTrue(user.contains("ONLY the reaction"))
        assertTrue(user.contains("Looks like we have a new guest."))
    }

    @Test
    fun `personality duplicate check is case and punctuation blind`() {
        val recent = ArrayDeque(listOf("okayiseeyou"))
        assertTrue(OnlineAiPersonality.isDuplicateOfRecent("Okay, I see you!", recent))
        assertTrue(!OnlineAiPersonality.isDuplicateOfRecent("Slow down a little.", recent))
        assertTrue(!OnlineAiPersonality.isDuplicateOfRecent("   ", recent))
    }

    // ------------------------------------------------------------------
    // Online AI speech queue
    // ------------------------------------------------------------------

    @Test
    fun `speechQueue is fifo when calm`() {
        val queue = SpeechQueue()
        queue.offer(QueuedSpeech("a", OnlineAiSeverity.INFO))
        queue.offer(QueuedSpeech("b", OnlineAiSeverity.WARNING))
        assertEquals("a", queue.poll()?.text)
        assertEquals("b", queue.poll()?.text)
        assertEquals(null, queue.poll())
    }

    @Test
    fun `speechQueue fault evicts queued info`() {
        val queue = SpeechQueue()
        queue.offer(QueuedSpeech("info", OnlineAiSeverity.INFO))
        queue.offer(QueuedSpeech("fault", OnlineAiSeverity.FAULT))
        assertEquals("fault", queue.poll()?.text)
        assertEquals(null, queue.poll())
    }

    @Test
    fun `speechQueue drops lowest severity when over capacity`() {
        val queue = SpeechQueue(capacity = 2)
        queue.offer(QueuedSpeech("w1", OnlineAiSeverity.WARNING))
        queue.offer(QueuedSpeech("w2", OnlineAiSeverity.WARNING))
        queue.offer(QueuedSpeech("i", OnlineAiSeverity.INFO))
        assertEquals(2, queue.size)
        assertEquals("w1", queue.poll()?.text)
        assertEquals("w2", queue.poll()?.text)
    }

    // ------------------------------------------------------------------
    // Demo source
    // ------------------------------------------------------------------

    @Test
    fun `DemoObdSource ships a non-empty plausible scenario`() {
        assertTrue(DemoObdSource.storedCodes.isNotEmpty())
        assertEquals(DemoObdSource.storedCodes.size, DemoObdSource.milStatus.storedCount)
        assertTrue(DemoObdSource.milStatus.milOn)
    }

    @Test
    fun `DemoObdSource live values stay within gauge ranges`() {
        for (second in 0..120) {
            val (speed, rpm, coolant) = DemoObdSource.liveValuesAt(second)
            assertTrue("speed $speed", speed in 0..220)
            assertTrue("rpm $rpm", rpm in 800..5200)
            assertTrue("coolant $coolant", coolant in 60..110)
        }
    }

    // ------------------------------------------------------------------
    // Phase 0: ELM sanitizer + status taxonomy (LTSupportAutomotive lessons)
    // ------------------------------------------------------------------

    @Test
    fun `sanitizeLines drops garbage bytes and empty lines`() {
        // Control bytes, DEL and non-ASCII clone noise must vanish; the
        // printable lines (and the ">" prompt) survive, empties drop out.
        val raw = "410D1F\r\r" + 0.toChar() + 1.toChar() + ">" +
            0x7F.toChar() + 0xA1.toChar() + "\n\n  \nSEARCHING...\r>"
        val lines = ElmSanitizer.sanitizeLines(raw)
        assertEquals(listOf("410D1F", ">", "SEARCHING...", ">"), lines)
    }

    @Test
    fun `isValidPidLine accepts hex and spaces only`() {
        assertTrue(ElmSanitizer.isValidPidLine("41 0D 1F"))
        assertTrue(ElmSanitizer.isValidPidLine("410D1F"))
        assertTrue(!ElmSanitizer.isValidPidLine("410DZZ"))
        assertTrue(!ElmSanitizer.isValidPidLine("NO DATA"))
        assertTrue(!ElmSanitizer.isValidPidLine(""))
        assertTrue(!ElmSanitizer.isValidPidLine("OK"))
    }

    @Test
    fun `classifyStatus names every adapter state`() {
        assertEquals(ElmStatus.NO_DATA, ElmSanitizer.classifyStatus("NO DATA\r\r>"))
        assertEquals(ElmStatus.NO_DATA, ElmSanitizer.classifyStatus("NODATA"))
        assertEquals(ElmStatus.SEARCHING, ElmSanitizer.classifyStatus("SEARCHING..."))
        assertEquals(ElmStatus.STOPPED, ElmSanitizer.classifyStatus("STOPPED"))
        assertEquals(ElmStatus.UNABLE_TO_CONNECT, ElmSanitizer.classifyStatus("UNABLE TO CONNECT"))
        assertEquals(ElmStatus.BUS_BUSY, ElmSanitizer.classifyStatus("BUS BUSY"))
        assertEquals(ElmStatus.BUS_ERROR, ElmSanitizer.classifyStatus("BUS ERROR"))
        assertEquals(ElmStatus.BUS_ERROR, ElmSanitizer.classifyStatus("BUSINIT: ERROR"))
        assertEquals(ElmStatus.CAN_ERROR, ElmSanitizer.classifyStatus("CAN ERROR"))
        assertEquals(ElmStatus.LOW_VOLTAGE_RESET, ElmSanitizer.classifyStatus("LVRESET"))
        assertEquals(ElmStatus.GENERIC_ERROR, ElmSanitizer.classifyStatus("?"))
        assertEquals(ElmStatus.OK, ElmSanitizer.classifyStatus("OK"))
        assertEquals(ElmStatus.NONE, ElmSanitizer.classifyStatus("410D1F"))
    }

    @Test
    fun `classifyStatus checks specifics before generic markers`() {
        // "?" must not swallow real states; "ERROR" must not swallow CAN errors.
        assertEquals(ElmStatus.UNABLE_TO_CONNECT, ElmSanitizer.classifyStatus("UNABLE TO CONNECT\r?"))
        assertEquals(ElmStatus.CAN_ERROR, ElmSanitizer.classifyStatus("CAN ERROR"))
        // "OK" inside ordinary words is not an adapter OK.
        assertEquals(ElmStatus.NONE, ElmSanitizer.classifyStatus("BROKEN"))
    }

    @Test
    fun `status recovery tells the caller what to do`() {
        assertEquals(ElmRecovery.NONE, ElmStatus.NONE.recovery)
        assertEquals(ElmRecovery.NONE, ElmStatus.NO_DATA.recovery)
        assertEquals(ElmRecovery.RETRY, ElmStatus.SEARCHING.recovery)
        assertEquals(ElmRecovery.RETRY, ElmStatus.STOPPED.recovery)
        assertEquals(ElmRecovery.REINIT, ElmStatus.UNABLE_TO_CONNECT.recovery)
        assertEquals(ElmRecovery.REINIT, ElmStatus.CAN_ERROR.recovery)
        assertEquals(ElmRecovery.REINIT, ElmStatus.LOW_VOLTAGE_RESET.recovery)
        assertEquals(ElmRecovery.RETRY, ElmStatus.GENERIC_ERROR.recovery)
    }

    @Test
    fun `splitErrors drops CAN voltage and timeout chatter`() {
        assertEquals(listOf("P0301"), ObdHelper.splitErrors("P0301 CAN ERROR\r\n>"))
        assertEquals(emptyList<String>(), ObdHelper.splitErrors("BUS BUSY"))
        assertEquals(emptyList<String>(), ObdHelper.splitErrors("BUSINIT"))
        assertEquals(emptyList<String>(), ObdHelper.splitErrors("LOW VOLTAGE LVRESET"))
        assertEquals(emptyList<String>(), ObdHelper.splitErrors("TIMEOUT FCRX"))
    }

    // ------------------------------------------------------------------
    // Phase 0: per-PID health tracker (AndrOBD 3-strike lesson)
    // ------------------------------------------------------------------

    @Test
    fun `pid tracker disables a pid after three straight failures`() {
        val tracker = PidHealthTracker()
        assertTrue(!tracker.recordFailure(0x0C))
        assertTrue(!tracker.recordFailure(0x0C))
        assertTrue(tracker.recordFailure(0x0C))
        assertTrue(tracker.isDisabled(0x0C))
        assertEquals(setOf(0x0C), tracker.disabledPids())
    }

    @Test
    fun `pid tracker success resets the streak`() {
        val tracker = PidHealthTracker()
        tracker.recordFailure(0x0D)
        tracker.recordFailure(0x0D)
        tracker.recordSuccess(0x0D)
        tracker.recordFailure(0x0D)
        tracker.recordFailure(0x0D)
        assertTrue(!tracker.isDisabled(0x0D))
    }

    @Test
    fun `pid tracker isolates pids and resets cleanly`() {
        val tracker = PidHealthTracker()
        tracker.recordFailure(0x0C)
        tracker.recordFailure(0x0C)
        tracker.recordFailure(0x0C)
        assertTrue(!tracker.isDisabled(0x0D))
        tracker.reset()
        assertTrue(!tracker.isDisabled(0x0C))
        assertTrue(tracker.disabledPids().isEmpty())
    }

    // ------------------------------------------------------------------
    // Ask-AI follow-up: context builders (pure, testable prompt logic)
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // Code-source pills: which read mode reported each code
    // ------------------------------------------------------------------

    @Test
    fun `mergeCodeSources unions modes per code`() {
        val merged = ObdHelper.mergeCodeSources(
            stored = listOf("P0301", "P0420"),
            pending = listOf("P0420", "P0455"),
            permanent = listOf("P0455")
        )
        assertEquals(setOf(DtcSource.STORED), merged.getValue("P0301"))
        assertEquals(setOf(DtcSource.STORED, DtcSource.PENDING), merged.getValue("P0420"))
        assertEquals(setOf(DtcSource.PENDING, DtcSource.PERMANENT), merged.getValue("P0455"))
    }

    @Test
    fun `mergeCodeSources normalizes case and skips blanks`() {
        val merged = ObdHelper.mergeCodeSources(
            stored = listOf("p0301", "  "),
            pending = emptyList(),
            permanent = listOf("P0301")
        )
        assertEquals(setOf(DtcSource.STORED, DtcSource.PERMANENT), merged.getValue("P0301"))
        assertEquals(1, merged.size)
    }

    @Test
    fun `mergeCodeSources is empty when every mode is empty`() {
        assertTrue(ObdHelper.mergeCodeSources(emptyList(), emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `DtcStore round-trips code sources`() {
        val dto = DtpCodeDTO(
            "P0301", ErrorSeverity.HIGH, "T", "D", "I", listOf("A"),
            sources = setOf(DtcSource.STORED, DtcSource.PENDING)
        )
        val parsed = DtcStore.fromJson(DtcStore.toJson(listOf(dto)))
        assertEquals(setOf(DtcSource.STORED, DtcSource.PENDING), parsed.single().sources)
    }

    @Test
    fun `DtcStore ignores unknown future sources`() {
        val dto = DtpCodeDTO("P0301", ErrorSeverity.LOW, "T", "D", "I", emptyList())
        val json = DtcStore.toJson(listOf(dto)).replace("\"sources\":[]", "\"sources\":[\"FUTURE\"]")
        assertTrue(DtcStore.fromJson(json).single().sources.isEmpty())
    }

    // ------------------------------------------------------------------
    // OBD console: bounded transcript ring
    // ------------------------------------------------------------------

    @Test
    fun `console log keeps newest lines within capacity`() {
        val log = ConsoleLog(capacity = 3)
        log.append("a")
        log.append("b")
        log.append("c")
        log.append("d")
        assertEquals(listOf("b", "c", "d"), log.snapshot())
    }

    @Test
    fun `console log clears fully`() {
        val log = ConsoleLog()
        log.append("a")
        log.clear()
        assertTrue(log.snapshot().isEmpty())
    }

    // ------------------------------------------------------------------
    // Offline DTC dictionary (bundled generic knowledge, no network)
    // ------------------------------------------------------------------

    @Test
    fun `dtc dictionary parses entries and skips bad rows`() {
        val table = DtcDictionary.fromJson(
            """[{"code":"P0420","title":"T","detail":"D","implications":"I","actions":["A"]},
               |{"code":"","title":"bad"},
               |{"nope":true}]""".trimMargin()
        )
        assertEquals(1, table.size)
        assertEquals("T", table.getValue("P0420").title)
    }

    @Test
    fun `dtc dictionary never throws on garbage`() {
        assertTrue(DtcDictionary.fromJson("not json{{").isEmpty())
        assertTrue(DtcDictionary.fromJson("").isEmpty())
    }

    @Test
    fun `dtc dictionary lookup covers exact and misfire family`() {
        val table = DtcDictionary.fromJson(
            """[{"code":"P0300","title":"Misfire","detail":"D","implications":"I","actions":[]}]"""
        )
        assertEquals("Misfire", DtcDictionary.lookup("P0301", table)?.title)
        assertEquals("Misfire", DtcDictionary.lookup("p0312", table)?.title)
        assertEquals(null, DtcDictionary.lookup("P0420", table))
    }

    @Test
    fun `bundled dictionary parses and covers the demo codes`() {
        val raw = java.io.File("src/main/res/raw/dtc_generic.json").readText()
        val table = DtcDictionary.fromJson(raw)
        assertTrue("dictionary too small: ${table.size}", table.size >= 20)
        for (code in DemoObdSource.storedCodes + DemoObdSource.pendingCodes) {
            assertTrue("no entry for $code", DtcDictionary.lookup(code, table) != null)
        }
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

    // ------------------------------------------------------------------
    // Freeze frame (mode 02 reuses mode 01 math)
    // ------------------------------------------------------------------

    @Test
    fun `freeze dtc bytes decode like stored codes`() {
        assertEquals("P0301", FreezeFrame.decodeDtcBytes(0x03, 0x01))
        assertEquals("U0123", FreezeFrame.decodeDtcBytes(0xC1, 0x23))
        assertEquals(null, FreezeFrame.decodeDtcBytes(0x00, 0x00))
    }

    @Test
    fun `freeze frame parses a full mode 02 reply set`() {
        val frame = FreezeFrame.parse(
            dtcRaw = "42020301",
            rpmRaw = "420C1AF8",
            speedRaw = "420D1F",
            coolantRaw = "42055A",
            loadRaw = "420482"
        )
        assertEquals("P0301", frame.dtc)
        assertEquals(1726, frame.rpm)
        assertEquals(31, frame.speedKmh)
        assertEquals(50, frame.coolantC)
        assertEquals(50, frame.loadPct)
    }

    @Test
    fun `freeze frame yields nulls for missing or truncated replies`() {
        val frame = FreezeFrame.parse("NO DATA", "420C1A", null, "4205ZZ", "")
        assertEquals(null, frame.dtc)
        assertEquals(null, frame.rpm)
        assertEquals(null, frame.speedKmh)
        assertEquals(null, frame.coolantC)
        assertEquals(null, frame.loadPct)
    }

    // ------------------------------------------------------------------
    // Connection robustness: typed recovery + batched fast reads
    // ------------------------------------------------------------------

    @Test
    fun `recoveryFor maps adapter failures to re-init`() {
        fun raw(text: String) = com.github.eltonvs.obd.command.ObdRawResponse(text, 0L)
        assertEquals(
            ElmRecovery.REINIT,
            ObdHelper.recoveryFor(
                com.github.eltonvs.obd.command.UnableToConnectException(MySpeedCommand(), raw("UNABLE TO CONNECT"))
            )
        )
        assertEquals(
            ElmRecovery.REINIT,
            ObdHelper.recoveryFor(
                com.github.eltonvs.obd.command.BusInitException(MySpeedCommand(), raw("BUS INIT... ERROR"))
            )
        )
    }

    @Test
    fun `recoveryFor retries stops and ignores no-data`() {
        fun raw(text: String) = com.github.eltonvs.obd.command.ObdRawResponse(text, 0L)
        assertEquals(
            ElmRecovery.RETRY,
            ObdHelper.recoveryFor(
                com.github.eltonvs.obd.command.StoppedException(MySpeedCommand(), raw("STOPPED"))
            )
        )
        assertEquals(
            ElmRecovery.NONE,
            ObdHelper.recoveryFor(
                com.github.eltonvs.obd.command.NoDataException(MySpeedCommand(), raw("NO DATA"))
            )
        )
        assertEquals(
            ElmRecovery.NONE,
            ObdHelper.recoveryFor(
                com.github.eltonvs.obd.command.UnSupportedCommandException(MySpeedCommand(), raw("?"))
            )
        )
        assertEquals(ElmRecovery.RETRY, ObdHelper.recoveryFor(java.io.IOException("boom")))
    }

    @Test
    fun `fast batch parses four gauges from one reply`() {
        val batch = ObdHelper.parseFastBatch("410C1AF8410D1F41055A410482\r\r>")
        assertEquals("1726", batch[PidRegistry.PID_RPM])
        assertEquals("31", batch[PidRegistry.PID_SPEED])
        assertEquals("50", batch[PidRegistry.PID_COOLANT_TEMP])
        assertEquals("50", batch[PidRegistry.PID_ENGINE_LOAD])
    }

    @Test
    fun `fast batch yields blanks for missing frames`() {
        val batch = ObdHelper.parseFastBatch("NO DATA\r\r>")
        assertEquals(4, batch.size)
        assertTrue(batch.values.all { it.isEmpty() })
    }

    // ------------------------------------------------------------------
    // Display units: SI underneath, metric/imperial only on screen
    // ------------------------------------------------------------------

    @Test
    fun `units convert speed and temperature`() {
        assertEquals(62.1371, Units.kmhToMph(100.0), 0.001)
        assertEquals(32.0, Units.cToF(0.0), 0.001)
        assertEquals(212.0, Units.cToF(100.0), 0.001)
    }

    @Test
    fun `units gauge adapts to imperial`() {
        assertEquals(140f, Units.speedGaugeMax(true))
        assertEquals(220f, Units.speedGaugeMax(false))
        assertEquals("mph", Units.speedUnitLabel(true))
        assertEquals("Km/h", Units.speedUnitLabel(false))
    }

    @Test
    fun `units display helpers pass metric through`() {
        assertEquals(100.0, Units.displaySpeed(100.0, false), 0.0)
        assertEquals(90.0, Units.displayTemp(90.0, false), 0.0)
        assertEquals(Units.kmhToMph(100.0), Units.displaySpeed(100.0, true), 0.0)
        assertEquals(Units.cToF(90.0), Units.displayTemp(90.0, true), 0.0)
    }

    // ------------------------------------------------------------------
    // Trip computer: OBD-only stats, no GPS needed
    // ------------------------------------------------------------------

    @Test
    fun `trip integrates distance from speed over time`() {
        val trip = TripComputer()
        trip.start(0L)
        // 100 km/h held for 36 s = exactly 1 km.
        for (s in 1..36) trip.sample(100.0, 2500, s * 1000L)
        val snap = trip.snapshot(36_000L)
        assertEquals(1.0, snap.distanceKm, 0.01)
        assertEquals(100.0, snap.avgKmh, 0.5)
        assertEquals(100.0, snap.maxKmh, 0.0)
        assertEquals(2500, snap.maxRpm)
    }

    @Test
    fun `trip splits idle and drive time`() {
        val trip = TripComputer()
        trip.start(0L)
        for (s in 1..10) trip.sample(0.0, 800, s * 1000L)
        for (s in 11..20) trip.sample(60.0, 2000, s * 1000L)
        val snap = trip.snapshot(20_000L)
        assertEquals(10_000L, snap.idleMs)
        assertEquals(10_000L, snap.driveMs)
        assertEquals(20_000L, snap.durationMs)
    }

    @Test
    fun `trip stop freezes totals and reset clears`() {
        val trip = TripComputer()
        trip.start(0L)
        trip.sample(50.0, 2000, 10_000L)
        trip.stop(10_000L)
        trip.sample(50.0, 2000, 20_000L)
        assertEquals(10_000L, trip.snapshot(30_000L).durationMs)
        trip.reset()
        val snap = trip.snapshot(30_000L)
        assertEquals(0L, snap.durationMs)
        assertEquals(0.0, snap.distanceKm, 0.0)
        assertEquals(0, snap.maxRpm)
    }

    @Test
    fun `trip tolerates nulls and ignores samples before start`() {
        val trip = TripComputer()
        trip.sample(80.0, 3000, 5_000L)
        trip.start(10_000L)
        trip.sample(null, null, 11_000L)
        val snap = trip.snapshot(11_000L)
        assertEquals(0.0, snap.distanceKm, 0.0)
        assertEquals(0, snap.maxRpm)
        assertEquals(1_000L, snap.durationMs)
    }

    @Test
    fun `trip formats durations as h-mm-ss`() {
        assertEquals("0:00:00", TripComputer.formatDuration(0L))
        assertEquals("0:01:05", TripComputer.formatDuration(65_000L))
        assertEquals("2:10:00", TripComputer.formatDuration(7_800_000L))
    }
}
