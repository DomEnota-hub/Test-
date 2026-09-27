from pathlib import Path

roots = [Path("app"), Path("patch/app")]


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected marker once, got {count}: {old[:100]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


for root in roots:
    p = root / "src/main/java/ru/railbrake/calculator/core/assistant/AssistantModels.kt"
    replace_once(p, "    ERMAK_DIAGNOSTIC,\n    KNOWLEDGE_ARTICLE\n", "    ERMAK_DIAGNOSTIC,\n    KNOWLEDGE_ARTICLE,\n    FIRST_AID\n")
    replace_once(
        p,
        "    data class Knowledge(\n        val articleId: String\n    ) : AssistantTarget\n",
        "    data class Knowledge(\n        val articleId: String\n    ) : AssistantTarget\n\n    data class FirstAid(\n        val topicId: String\n    ) : AssistantTarget\n",
    )
    replace_once(
        p,
        "        append(title)\n        append(' ')\n        append(summary)\n",
        "        append(canonicalId)\n        append(' ')\n        append(key)\n        append(' ')\n        append(title)\n        append(' ')\n        append(summary)\n",
    )
    replace_once(
        p,
        "        append(tags.joinToString(\" \"))\n        append(' ')\n        append(nativeSearchText)\n",
        "        append(tags.joinToString(\" \"))\n        append(' ')\n        append(relatedIds.joinToString(\" \"))\n        append(' ')\n        append(nativeSearchText)\n",
    )

for root in roots:
    p = root / "src/main/java/ru/railbrake/calculator/ui/FirstAidScreen.kt"
    replace_once(p, "private val firstAidSearchKeywords = mapOf(", "internal val firstAidSearchKeywords = mapOf(")
    replace_once(p, "private val workerKitLines = listOf(", "internal val workerKitLines = listOf(")
    replace_once(
        p,
        'fun FirstAidScreen(onBack: () -> Unit) {\n    val context = LocalContext.current\n    var selectedId by rememberSaveable { mutableStateOf("unconscious") }',
        'fun FirstAidScreen(onBack: () -> Unit, initialTopicId: String? = null) {\n    val context = LocalContext.current\n    var selectedId by rememberSaveable(initialTopicId) {\n        mutableStateOf(\n            initialTopicId?.takeIf { candidate ->\n                candidate == "kit" || firstAidTopics.any { it.id == candidate }\n            } ?: "unconscious"\n        )\n    }',
    )

first_aid_adapter = '''\n\nobject FirstAidAssistantAdapter {\n    fun adapt(topic: FirstAidTopic): AssistantDocument = AssistantDocument(\n        key = "first-aid:${topic.id}",\n        canonicalId = topic.id,\n        kind = AssistantDocumentKind.FIRST_AID,\n        family = null,\n        section = TechnicalSection.SAFETY,\n        title = topic.title,\n        summary = topic.whenToUse.firstOrNull().orEmpty(),\n        body = buildList {\n            addAll(topic.whenToUse)\n            addAll(topic.actions)\n            addAll(topic.dont)\n            topic.note?.let(::add)\n            add(topic.source)\n        }.joinToString(" "),\n        aliases = buildSet {\n            add(topic.title)\n            addAll(topic.whenToUse)\n            addAll(firstAidSearchKeywords[topic.id].orEmpty())\n        },\n        componentIds = emptySet(),\n        symptomTerms = topic.whenToUse.toSet(),\n        tags = setOf("первая помощь", "оказание первой помощи", "опп"),\n        relatedIds = emptySet(),\n        safetyCritical = true,\n        target = AssistantTarget.FirstAid(topic.id),\n        nativeSearchText = firstAidSearchKeywords[topic.id].orEmpty().joinToString(" ")\n    )\n}\n'''

for root in roots:
    p = root / "src/main/java/ru/railbrake/calculator/core/assistant/AssistantAdapters.kt"
    text = p.read_text(encoding="utf-8")
    marker = "import ru.railbrake.calculator.core.TechnicalSection\n"
    if text.count(marker) != 1:
        raise SystemExit(f"{p}: import marker mismatch")
    text = text.replace(
        marker,
        marker + "import ru.railbrake.calculator.ui.FirstAidTopic\nimport ru.railbrake.calculator.ui.firstAidSearchKeywords\n",
        1,
    )
    if "object FirstAidAssistantAdapter" in text:
        raise SystemExit(f"{p}: adapter already exists")
    p.write_text(text.rstrip() + first_aid_adapter + "\n", encoding="utf-8")

for root in roots:
    p = root / "src/main/java/ru/railbrake/calculator/core/assistant/AssistantContentLoader.kt"
    text = p.read_text(encoding="utf-8")
    marker_import = "import ru.railbrake.calculator.core.TechnicalSection\n"
    if text.count(marker_import) != 1:
        raise SystemExit(f"{p}: loader import marker mismatch")
    text = text.replace(
        marker_import,
        marker_import + "import ru.railbrake.calculator.ui.firstAidTopics\nimport ru.railbrake.calculator.ui.workerKitLines\n",
        1,
    )
    marker = '''        val knowledge = KnowledgeRepository.articles\n            .map(KnowledgeArticleAssistantAdapter::adapt)\n\n        return (technical + vl80Diagnostics + ermakDiagnostics + knowledge)\n            .distinctBy(AssistantDocument::key)\n'''
    replacement = '''        val knowledge = KnowledgeRepository.articles\n            .map(KnowledgeArticleAssistantAdapter::adapt)\n\n        val firstAid = firstAidTopics.map(FirstAidAssistantAdapter::adapt)\n        val firstAidKit = AssistantDocument(\n            key = "first-aid:kit",\n            canonicalId = "kit",\n            kind = AssistantDocumentKind.FIRST_AID,\n            family = null,\n            section = TechnicalSection.SAFETY,\n            title = "Аптечка работника",\n            summary = "Состав аптечки и средства первой помощи",\n            body = workerKitLines.joinToString(" "),\n            aliases = setOf("аптечка", "аптечка работника", "жгут", "бинт", "перчатки", "салфетки"),\n            componentIds = emptySet(),\n            symptomTerms = emptySet(),\n            tags = setOf("первая помощь", "оказание первой помощи", "опп"),\n            relatedIds = emptySet(),\n            safetyCritical = true,\n            target = AssistantTarget.FirstAid("kit")\n        )\n\n        return (technical + vl80Diagnostics + ermakDiagnostics + knowledge + firstAid + firstAidKit)\n            .distinctBy(AssistantDocument::key)\n'''
    if text.count(marker) != 1:
        raise SystemExit(f"{p}: loader marker mismatch")
    p.write_text(text.replace(marker, replacement, 1), encoding="utf-8")

for root in roots:
    p = root / "src/main/java/ru/railbrake/calculator/core/assistant/AssistantIndex.kt"
    replace_once(
        p,
        "            val reasons = mutableListOf<String>()\n\n            if (query.isNotBlank() && query in document.searchText) {",
        '''            val reasons = mutableListOf<String>()\n\n            val canonicalId = document.canonicalId.normalizeAssistantText()\n            val directCanonicalId = canonicalId.isNotBlank() &&\n                (query == canonicalId || query.split(' ').contains(canonicalId) ||\n                    (canonicalId.length >= 5 && query.contains(canonicalId)))\n            if (directCanonicalId) {\n                score += 180\n                hasContentEvidence = true\n                reasons += "canonical-id"\n            }\n\n            val linkedId = (document.relatedIds + document.componentIds)\n                .asSequence()\n                .map(String::normalizeAssistantText)\n                .filter(String::isNotBlank)\n                .firstOrNull { candidate ->\n                    query == candidate || query.split(' ').contains(candidate) ||\n                        (candidate.length >= 5 && query.contains(candidate))\n                }\n            if (linkedId != null) {\n                score += 90\n                hasContentEvidence = true\n                reasons += "linked-id"\n            }\n\n            if (query.isNotBlank() && query in document.searchText) {''',
    )
    replace_once(
        p,
        '            "где", "покажи", "найди", "открой", "про", "при", "это", "он", "она"\n',
        '            "где", "покажи", "найди", "открой", "про", "при", "это", "он", "она",\n            "id", "ид", "айди", "карточка", "карточку", "сценарий", "сценария"\n',
    )

for root in roots:
    p = root / "src/main/java/ru/railbrake/calculator/core/assistant/AssistantQueryParser.kt"
    replace_once(
        p,
        '        "главник" to "главный выключатель",\n',
        '''        "главник" to "главный выключатель",\n        "групповик" to "групповой переключатель",\n        "фазник" to "фазорасщепитель",\n        "машинистский кран" to "кран машиниста",\n        "кран триста девяносто пять" to "км 395",\n        "кран 395" to "км 395",\n        "не пашет" to "не работает",\n        "не фурычит" to "не работает",\n        "вырубился" to "самопроизвольно отключился",\n        "вырубило" to "самопроизвольно отключило",\n        "отрубился" to "самопроизвольно отключился",\n        "отвалился" to "самопроизвольно отключился",\n        "залипает" to "заклинивает",\n        "залип" to "заклинил",\n        "сифонит" to "утечка воздуха",\n        "травит" to "утечка",\n''',
    )
    replace_once(
        p,
        '        ComponentVocabulary("COMPRESSOR", listOf("компрессор", "мк"), "компрессор мотор-компрессор мк"),\n',
        '        ComponentVocabulary("COMPRESSOR", listOf("компрессор", "мотор-компрессор", "мотор компрессор", "мк"), "компрессор мотор-компрессор мк"),\n',
    )
    replace_once(
        p,
        '        ComponentVocabulary("EKG", listOf("экг", "групповой переключатель"), "экг групповой переключатель"),\n',
        '        ComponentVocabulary("EKG", listOf("экг", "групповой переключатель", "групповик"), "экг групповой переключатель групповик"),\n',
    )
    replace_once(
        p,
        '        ComponentVocabulary("PHASE_SPLITTER", listOf("фазорасщепитель", "расщепитель фаз"), "фазорасщепитель расщепитель фаз"),\n',
        '        ComponentVocabulary("PHASE_SPLITTER", listOf("фазорасщепитель", "расщепитель фаз", "фазник"), "фазорасщепитель расщепитель фаз фазник"),\n',
    )
    replace_once(
        p,
        '        ComponentVocabulary("AIR_DISTRIBUTOR", listOf("воздухораспределитель", "вр 483", "вр483"), "воздухораспределитель вр 483"),\n',
        '        ComponentVocabulary("AIR_DISTRIBUTOR", listOf("воздухораспределитель", "вр 483", "вр483", "вр", "483"), "воздухораспределитель вр 483"),\n',
    )
    replace_once(
        p,
        '        ComponentVocabulary("DRIVER_BRAKE_VALVE", listOf("кран машиниста", "км 395", "км395"), "кран машиниста км 395"),\n',
        '        ComponentVocabulary("DRIVER_BRAKE_VALVE", listOf("кран машиниста", "машинистский кран", "км 395", "км395", "395"), "кран машиниста км 395"),\n',
    )
    replace_once(
        p,
        '        "самопроизвольно", "сам включ", "сам выключ", "мигает", "моргает", "горит постоянно"\n',
        '        "самопроизвольно", "сам включ", "сам выключ", "мигает", "моргает", "горит постоянно",\n        "глюч", "косяч", "выруб", "отруб", "отвал", "залип", "трав", "сифон",\n        "не останавлива", "пахнет гарью", "гарь", "трещит", "дребезжит", "воет", "свистит"\n',
    )
    replace_once(
        p,
        '            .replace(Regex("[^\\\\p{L}\\\\p{N}\\\\s-]"), " ")',
        '            .replace(Regex("[^\\\\p{L}\\\\p{N}\\\\s_:-]"), " ")',
    )
    replace_once(
        p,
        '        listOf("охрана труда", "безопасность", "переохлаж", "обморож", "первая помощь").any(text::contains)\n',
        '''        listOf(\n            "охрана труда", "безопасность", "первая помощь", "опп", "переохлаж", "обморож",\n            "слр", "реанимац", "без сознания", "не дышит", "кровотеч", "подавил", "ожог",\n            "удар током", "электроудар", "отрав", "перелом", "судорог", "укус", "тепловой удар",\n            "замерз", "обмороз", "аптеч"\n        ).any(text::contains)\n''',
    )

for root in roots:
    p = root / "src/main/java/ru/railbrake/calculator/ui/AssistantResultActivity.kt"
    replace_once(
        p,
        '            KIND_KNOWLEDGE -> {\n                KnowledgeBaseScreen(',
        '''            KIND_FIRST_AID -> {\n                FirstAidScreen(\n                    onBack = { finish() },\n                    initialTopicId = id\n                )\n            }\n\n            KIND_KNOWLEDGE -> {\n                KnowledgeBaseScreen(''',
    )
    replace_once(
        p,
        '        private const val KIND_KNOWLEDGE = "knowledge"\n',
        '        private const val KIND_KNOWLEDGE = "knowledge"\n        private const val KIND_FIRST_AID = "first_aid"\n',
    )
    replace_once(
        p,
        '''                    is AssistantTarget.Knowledge -> {\n                        putExtra(EXTRA_KIND, KIND_KNOWLEDGE)\n                        putExtra(EXTRA_ID, target.articleId)\n                    }\n''',
        '''                    is AssistantTarget.Knowledge -> {\n                        putExtra(EXTRA_KIND, KIND_KNOWLEDGE)\n                        putExtra(EXTRA_ID, target.articleId)\n                    }\n\n                    is AssistantTarget.FirstAid -> {\n                        putExtra(EXTRA_KIND, KIND_FIRST_AID)\n                        putExtra(EXTRA_ID, target.topicId)\n                    }\n''',
    )

test_addition = '''\n\n    @Test\n    fun directCanonicalIdReferenceWinsEvenWithConversationalWrapper() {\n        val result = engine().query("открой ID vl80-gv-fault")\n\n        assertTrue(result is AssistantEngineResult.Matches)\n        result as AssistantEngineResult.Matches\n        assertEquals("vl80-gv-fault", result.hits.first().document.canonicalId)\n        assertTrue("canonical-id" in result.hits.first().reasons)\n    }\n\n    @Test\n    fun conversationalRailwaySlangNormalizesToFaultIntent() {\n        val cases = mapOf(\n            "групповик залип" to "EKG",\n            "фазник не пашет" to "PHASE_SPLITTER",\n            "кран 395 травит" to "DRIVER_BRAKE_VALVE",\n            "мотор компрессор сифонит" to "COMPRESSOR"\n        )\n\n        cases.forEach { (query, component) ->\n            val parsed = AssistantQueryParser.parse(query)\n            assertEquals("Wrong component for: $query", component, parsed.componentKey)\n            assertEquals("Wrong intent for: $query", AssistantIntent.TROUBLESHOOT, parsed.intent)\n        }\n    }\n\n    @Test\n    fun firstAidWordingIsRecognizedAsSafetyIntent() {\n        val queries = listOf(\n            "человека ударило током",\n            "что делать если подавился",\n            "нужна слр",\n            "обморозил пальцы",\n            "что должно быть в аптечке"\n        )\n        queries.forEach { query ->\n            assertEquals("Wrong intent for: $query", AssistantIntent.SAFETY, AssistantQueryParser.parse(query).intent)\n        }\n    }\n'''

for root in roots:
    p = root / "src/test/java/ru/railbrake/calculator/core/assistant/AssistantEngineTest.kt"
    text = p.read_text(encoding="utf-8")
    marker = "\n    private fun engine(): AssistantEngine = AssistantEngine("
    if text.count(marker) != 1:
        raise SystemExit(f"{p}: engine marker mismatch")
    if "directCanonicalIdReferenceWinsEvenWithConversationalWrapper" in text:
        raise SystemExit(f"{p}: tests already added")
    p.write_text(text.replace(marker, test_addition + marker, 1), encoding="utf-8")

adapter_test = '''package ru.railbrake.calculator.core.assistant\n\nimport org.junit.Assert.assertEquals\nimport org.junit.Assert.assertTrue\nimport org.junit.Test\nimport ru.railbrake.calculator.ui.firstAidTopics\n\nclass FirstAidAssistantAdapterTest {\n    @Test\n    fun frostbiteTopicIsProjectedAsFirstAidDocument() {\n        val topic = firstAidTopics.first { it.id == "frostbite" }\n        val document = FirstAidAssistantAdapter.adapt(topic)\n\n        assertEquals(AssistantDocumentKind.FIRST_AID, document.kind)\n        assertEquals("frostbite", document.canonicalId)\n        assertEquals(AssistantTarget.FirstAid("frostbite"), document.target)\n        assertTrue("обморожение" in document.searchText)\n        assertTrue("первая помощь" in document.searchText)\n    }\n}\n'''

for root in roots:
    p = root / "src/test/java/ru/railbrake/calculator/core/assistant/FirstAidAssistantAdapterTest.kt"
    if p.exists():
        raise SystemExit(f"{p}: already exists")
    p.write_text(adapter_test, encoding="utf-8")

mirrored = [
    "src/main/java/ru/railbrake/calculator/core/assistant/AssistantModels.kt",
    "src/main/java/ru/railbrake/calculator/core/assistant/AssistantAdapters.kt",
    "src/main/java/ru/railbrake/calculator/core/assistant/AssistantContentLoader.kt",
    "src/main/java/ru/railbrake/calculator/core/assistant/AssistantIndex.kt",
    "src/main/java/ru/railbrake/calculator/core/assistant/AssistantQueryParser.kt",
    "src/main/java/ru/railbrake/calculator/ui/AssistantResultActivity.kt",
    "src/main/java/ru/railbrake/calculator/ui/FirstAidScreen.kt",
    "src/test/java/ru/railbrake/calculator/core/assistant/AssistantEngineTest.kt",
    "src/test/java/ru/railbrake/calculator/core/assistant/FirstAidAssistantAdapterTest.kt",
]
for rel in mirrored:
    if (Path("app") / rel).read_bytes() != (Path("patch/app") / rel).read_bytes():
        raise SystemExit(f"mirror mismatch: {rel}")

print("assistant v0.14 patch applied")
