package uk.scimone.diafit.i18n

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Guardrail for docs/i18n-plan.md: every language has the same keys, placeholders and plural forms. */
class StringResourcesTest {
    private val resDir = listOf(File("src/main/res"), File("app/src/main/res")).first { it.exists() }

    private class Entry(val placeholders: List<String>, val quantities: Set<String>?)

    private fun load(folder: String): Map<String, Entry> {
        val file = File(resDir, "$folder/strings.xml")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val out = mutableMapOf<String, Entry>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as? Element ?: continue
            if (e.getAttribute("translatable") == "false") continue
            val name = e.getAttribute("name")
            when (e.tagName) {
                "string" -> out[name] = Entry(placeholders(e.textContent), null)
                "plurals" -> {
                    val items = e.getElementsByTagName("item")
                    val q = mutableSetOf<String>()
                    val ph = mutableListOf<String>()
                    for (j in 0 until items.length) {
                        val item = items.item(j) as Element
                        q += item.getAttribute("quantity")
                        ph += placeholders(item.textContent)
                    }
                    out[name] = Entry(ph.distinct().sorted(), q)
                }
            }
        }
        return out
    }

    private fun placeholders(text: String): List<String> =
        Regex("%(\\d+\\$)?[sd]").findAll(text).map { it.value.replace(Regex("^%\\d+\\$"), "%") }.toList().sorted()

    private val en = load("values")

    private fun check(folder: String) {
        val other = load(folder)
        val missing = en.keys - other.keys
        val extra = other.keys - en.keys
        assertTrue("$folder is missing: $missing", missing.isEmpty())
        assertTrue("$folder has keys not in values/: $extra", extra.isEmpty())
        for ((key, e) in en) {
            val o = other.getValue(key)
            assertTrue("$folder/$key: plural/string kind differs", (e.quantities == null) == (o.quantities == null))
            if (e.quantities == null) {
                assertTrue("$folder/$key: placeholders ${o.placeholders} != ${e.placeholders}", o.placeholders == e.placeholders)
            } else {
                assertTrue("$folder/$key: plurals need one and other", o.quantities!!.containsAll(listOf("one", "other")))
            }
        }
    }

    @Test fun germanMatchesEnglish() = check("values-de")
    @Test fun italianMatchesEnglish() = check("values-it")
}
