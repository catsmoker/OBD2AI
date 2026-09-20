package com.catsmoker.obd2ai

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * File-based resource contracts: every locale mirrors the default string
 * keys, and every land/tablet layout variant keeps every default view ID
 * (a missing ID is a null findViewById crash on rotation).
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
}
