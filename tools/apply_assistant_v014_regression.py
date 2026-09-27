from pathlib import Path

for root in [Path("app"), Path("patch/app")]:
    parser = root / "src/main/java/ru/railbrake/calculator/core/assistant/AssistantQueryParser.kt"
    text = parser.read_text(encoding="utf-8")
    old = '            "удар током", "электроудар", "отрав", "перелом", "судорог", "укус", "тепловой удар",\n'
    new = '            "удар током", "ударило ток", "ударил ток", "шарахнуло ток", "электроудар", "отрав", "перелом", "судорог", "укус", "тепловой удар",\n'
    if text.count(old) != 1:
        raise SystemExit(f"{parser}: safety cue marker mismatch")
    parser.write_text(text.replace(old, new, 1), encoding="utf-8")

    test = root / "src/test/java/ru/railbrake/calculator/core/assistant/AssistantIndexTest.kt"
    text = test.read_text(encoding="utf-8")
    marker = "\n    private fun technicalEntry(\n"
    addition = '''
    @Test
    fun canonicalRelatedAndComponentIdsAreDirectlySearchable() {
        val document = AssistantDocument(
            key = "technical:ERMAK:EQUIPMENT:ER-EQ-GV",
            canonicalId = "ER-EQ-GV",
            kind = AssistantDocumentKind.TECHNICAL_ENTRY,
            family = TechnicalFamily.ERMAK,
            section = TechnicalSection.EQUIPMENT,
            title = "Главный выключатель",
            summary = "",
            body = "",
            aliases = setOf("ГВ"),
            componentIds = setOf("MAIN_BREAKER"),
            symptomTerms = emptySet(),
            tags = emptySet(),
            relatedIds = setOf("ER-DIAG-GV"),
            safetyCritical = false,
            target = AssistantTarget.Technical(
                TechnicalFamily.ERMAK,
                TechnicalSection.EQUIPMENT,
                "ER-EQ-GV"
            )
        )
        val index = InMemoryAssistantIndex(listOf(document))

        val canonical = index.search(AssistantSearchRequest("открой ID ER-EQ-GV")).single()
        assertTrue("canonical-id" in canonical.reasons)

        val related = index.search(AssistantSearchRequest("сценарий ER-DIAG-GV")).single()
        assertTrue("linked-id" in related.reasons)

        val component = index.search(AssistantSearchRequest("узел MAIN_BREAKER")).single()
        assertTrue("linked-id" in component.reasons)
    }
'''
    if text.count(marker) != 1:
        raise SystemExit(f"{test}: insertion marker mismatch")
    if "canonicalRelatedAndComponentIdsAreDirectlySearchable" in text:
        raise SystemExit(f"{test}: test already exists")
    test.write_text(text.replace(marker, "\n" + addition + marker, 1), encoding="utf-8")

pairs = [
    "src/main/java/ru/railbrake/calculator/core/assistant/AssistantQueryParser.kt",
    "src/test/java/ru/railbrake/calculator/core/assistant/AssistantIndexTest.kt",
]
for rel in pairs:
    if (Path("app") / rel).read_bytes() != (Path("patch/app") / rel).read_bytes():
        raise SystemExit(f"mirror mismatch: {rel}")

print("assistant v0.14 regression patch applied")
