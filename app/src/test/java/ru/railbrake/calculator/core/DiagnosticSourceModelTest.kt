package ru.railbrake.calculator.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticSourceModelTest {
    @Test
    fun parserPreservesExplicitNormativeVersionMetadata() {
        val root = JSONObject(
            """
            {
              "scenarios": [
                {
                  "id": "ER-TEST-SOURCE",
                  "title": "Тест источника",
                  "symptom": "Тест",
                  "sourceRefs": [
                    {
                      "sourceId": "norm-996r",
                      "document": "996/р",
                      "locator": "приложение",
                      "role": "primary",
                      "sourceKind": "NORMATIVE",
                      "version": {
                        "label": "2026-edition",
                        "revision": "rev-1",
                        "effectiveFrom": "2026-07-01",
                        "verifiedAt": "2026-09-25",
                        "status": "CURRENT_CONFIRMED"
                      }
                    }
                  ],
                  "graph": {
                    "startNodeId": "start",
                    "nodes": [
                      {"id": "start", "type": "result", "text": "Готово"}
                    ]
                  }
                }
              ]
            }
            """.trimIndent()
        )

        val source = parseErmakDiagnostics(root).single().sourceRefs.single()

        assertEquals(DiagnosticSourceKind.NORMATIVE, source.kind)
        assertEquals("2026-edition", source.version.versionLabel)
        assertEquals("rev-1", source.version.revision)
        assertEquals("2026-07-01", source.version.effectiveFrom)
        assertEquals("2026-09-25", source.version.verifiedAt)
        assertEquals(DiagnosticSourceVersionStatus.CURRENT_CONFIRMED, source.version.status)
        assertTrue(DiagnosticSourcePolicy.evaluate(source).usableForSourceBoundAction)
    }

    @Test
    fun confirmedManufacturerRevisionIsAccepted() {
        val source = DiagnosticSourceReference(
            sourceId = "manufacturer-re",
            document = "Руководство по эксплуатации",
            kind = DiagnosticSourceKind.MANUFACTURER,
            version = DiagnosticSourceVersion(
                revision = "rev-5",
                status = DiagnosticSourceVersionStatus.CURRENT_CONFIRMED
            )
        )

        assertTrue(DiagnosticSourcePolicy.evaluate(source).usableForSourceBoundAction)
    }

    @Test
    fun unknownOrUnversionedSourceFailsClosed() {
        val unknown = DiagnosticSourceReference(
            sourceId = "legacy-source",
            document = "Старый документ"
        )
        val unversioned = DiagnosticSourceReference(
            sourceId = "normative-source",
            document = "Нормативный документ",
            kind = DiagnosticSourceKind.NORMATIVE,
            version = DiagnosticSourceVersion(
                status = DiagnosticSourceVersionStatus.CURRENT_CONFIRMED
            )
        )

        assertFalse(DiagnosticSourcePolicy.evaluate(unknown).usableForSourceBoundAction)
        assertFalse(DiagnosticSourcePolicy.evaluate(unversioned).usableForSourceBoundAction)
    }

    @Test
    fun supersededVersionFailsClosed() {
        val source = DiagnosticSourceReference(
            sourceId = "normative-source",
            document = "Нормативный документ",
            kind = DiagnosticSourceKind.NORMATIVE,
            version = DiagnosticSourceVersion(
                versionLabel = "old-edition",
                status = DiagnosticSourceVersionStatus.SUPERSEDED
            )
        )

        assertFalse(DiagnosticSourcePolicy.evaluate(source).usableForSourceBoundAction)
    }

    @Test
    fun sourceSetRequiresAtLeastOneConfirmedCurrentVersion() {
        val old = DiagnosticSourceReference(
            sourceId = "old",
            document = "Старая редакция",
            kind = DiagnosticSourceKind.NORMATIVE,
            version = DiagnosticSourceVersion(
                versionLabel = "old",
                status = DiagnosticSourceVersionStatus.HISTORICAL
            )
        )
        val current = DiagnosticSourceReference(
            sourceId = "current",
            document = "Действующая редакция",
            kind = DiagnosticSourceKind.NORMATIVE,
            version = DiagnosticSourceVersion(
                versionLabel = "current",
                status = DiagnosticSourceVersionStatus.CURRENT_CONFIRMED
            )
        )

        assertFalse(DiagnosticSourcePolicy.evaluateAny(emptyList()).usableForSourceBoundAction)
        assertFalse(DiagnosticSourcePolicy.evaluateAny(listOf(old)).usableForSourceBoundAction)
        assertTrue(DiagnosticSourcePolicy.evaluateAny(listOf(old, current)).usableForSourceBoundAction)
    }
}
