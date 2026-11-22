package com.catsmoker.obd2ai.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the Online AI event detector, personality gates and voice queue. */
class OnlineAiTest {

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
}
