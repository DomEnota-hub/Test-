package ru.railbrake.calculator.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

class TechnicalDataAssetTest {
    private fun asset(name: String) =
        JSONObject(GZIPInputStream(File("src/main/assets/technical/$name.gz").inputStream()).bufferedReader().use { it.readText() })

    @Test
    fun ermakCanonicalPackagesAreComplete() {
        assertEquals(15, asset("ermak_system_map.json").getJSONArray("systems").length())
        assertEquals(111, asset("ermak_equipment.json").getJSONArray("records").length())
        assertEquals(138, asset("ermak_knowledge.json").getJSONArray("articles").length())
        assertEquals(136, asset("ermak_diagnostics.json").getJSONArray("scenarios").length())
        assertEquals(22, asset("ermak_schemes.json").getJSONArray("schemes").length())
        assertTrue(asset("ermak_links.json").getString("status").contains("PASS"))
    }

    @Test
    fun vl80sCanonicalPackagesAreComplete() {
        assertEquals(89, asset("vl80s_equipment.json").getJSONArray("records").length())
        assertEquals(66, asset("vl80s_acceptance.json").getJSONArray("items").length())
        assertEquals(8, asset("vl80s_electrical.json").getJSONArray("baseSchemes").length())
        assertEquals(8, asset("vl80s_pneumatic.json").getJSONArray("views").length())
        assertEquals(95, asset("vl80s_diagnostics.json").getJSONArray("scenarios").length())
    }

    @Test
    fun stableIdsRemainUniqueInsideEveryCatalog() {
        val catalogs = listOf(
            asset("ermak_equipment.json").getJSONArray("records") to "ER-EQ-",
            asset("ermak_knowledge.json").getJSONArray("articles") to "ER-KB-",
            asset("ermak_diagnostics.json").getJSONArray("scenarios") to "ER-DIAG-",
            asset("ermak_schemes.json").getJSONArray("schemes") to "ER-SCH-",
            asset("vl80s_equipment.json").getJSONArray("records") to "VL-EQ-",
            asset("vl80s_acceptance.json").getJSONArray("items") to "VL80-ACC-",
            asset("vl80s_electrical.json").getJSONArray("baseSchemes") to "VL-SCH-",
            asset("vl80s_pneumatic.json").getJSONArray("views") to "VL-SCH-"
        )
        catalogs.forEach { (items, prefix) ->
            val ids = (0 until items.length()).map { items.getJSONObject(it).getString("id") }
            assertEquals(ids.size, ids.toSet().size)
            assertTrue(ids.all { it.startsWith(prefix) })
        }
    }

    @Test
    fun everyErmakLocationTokenHasOneReadableRussianLabel() {
        val records = asset("ermak_equipment.json").getJSONArray("records")
        val tokens = buildSet<String> {
            for (index in 0 until records.length()) {
                val location = records.getJSONObject(index).optJSONObject("location") ?: continue
                listOf("sectionScope", "zone").forEach { key ->
                    location.optString(key).takeIf(String::isNotBlank)?.let(::add)
                }
                val sectionKinds = location.optJSONArray("sectionKinds")
                if (sectionKinds != null) {
                    for (kindIndex in 0 until sectionKinds.length()) add(sectionKinds.getString(kindIndex))
                }
            }
        }

        assertEquals(78, tokens.size)
        tokens.forEach { token ->
            val label = technicalLocationLabel(token)
            assertTrue("Нет отображаемого названия для $token", !label.isNullOrBlank())
            assertFalse("В UI остался внутренний ID $token", label.orEmpty().contains('_'))
        }
        assertEquals("пневматический блок", technicalLocationLabel("pneumatic_block"))
        assertEquals("группа резервуаров", technicalLocationLabel("reservoir_group"))
        assertEquals("нагнетательная линия компрессора", technicalLocationLabel("compressor_discharge_line"))
    }
}
