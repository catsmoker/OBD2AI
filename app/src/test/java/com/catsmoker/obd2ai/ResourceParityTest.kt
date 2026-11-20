package com.catsmoker.obd2ai

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * File-based resource contract: every locale mirrors the default string
 * keys. (The layout-ID parity check retired with the XML layouts — the UI
 * is now fully Compose, so there are no layout variants to keep in sync.)
 * Test JVM cwd is the `app/` module dir, so paths are `src/main/res/…`.
 */
class ResourceParityTest {

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
}
