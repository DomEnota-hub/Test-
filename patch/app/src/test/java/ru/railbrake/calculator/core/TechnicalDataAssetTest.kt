package ru.railbrake.calculator.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
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
        assertEquals(135, asset("ermak_diagnostics.json").getJSONArray("scenarios").length())
        assertEquals(22, asset("ermak_schemes.json").getJSONArray("schemes").length())
        assertTrue(asset("ermak_links.json").getString("status").contains("PASS"))
    }

    @Test
    fun vl80sCanonicalPackagesAreComplete() {
        assertEquals(89, asset("vl80s_equipment.json").getJSONArray("records").length())
        assertEquals(66, asset("vl80s_acceptance.json").getJSONArray("items").length())
        assertEquals(8, asset("vl80s_electrical.json").getJSONArray("baseSchemes").length())
        assertEquals(8, asset("vl80s_pneumatic.json").getJSONArray("views").length())
        assertEquals(93, asset("vl80s_diagnostics.json").getJSONArray("scenarios").length())
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
}
