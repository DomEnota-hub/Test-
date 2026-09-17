package ru.railbrake.calculator.core

import android.content.Context
import java.util.zip.GZIPInputStream
import org.json.JSONArray
import org.json.JSONObject

enum class TechnicalFamily(val title: String, val subtitle: String) {
    VL80S("ВЛ80С", "Секционный профиль с учётом исполнений"),
    ERMAK("Ермак", "2ЭС5К / 3ЭС5К")
}

enum class TechnicalSection(val title: String) {
    PROFILES("Исполнения"),
    EQUIPMENT("Оборудование"),
    KNOWLEDGE("Статьи"),
    DIAGNOSTICS("Диагностика"),
    ELECTRICAL("Электросхемы"),
    PNEUMATIC("Пневмосхемы"),
    ACCEPTANCE("Приёмка"),
    SYSTEMS("Системы")
}

data class TechnicalBlock(
    val title: String,
    val lines: List<String>
)

data class TechnicalEntry(
    val id: String,
    val family: TechnicalFamily,
    val section: TechnicalSection,
    val title: String,
    val subtitle: String,
    val status: String,
    val blocks: List<TechnicalBlock>,
    val relatedIds: List<String> = emptyList(),
    val sequence: List<String> = emptyList(),
    val searchText: String
)

class TechnicalDataRepository(private val context: Context) {
    companion object {
        private val sharedSectionCache = mutableMapOf<Pair<TechnicalFamily, TechnicalSection>, List<TechnicalEntry>>()
    }

    private val legacyEquipmentIds by lazy {
        json("technical/vl80s_equipment.json").array("records").objects()
            .mapNotNull { item ->
                item.optString("legacyId").takeIf(String::isNotBlank)?.let { legacy ->
                    "vl80-eq-$legacy" to item.optString("id")
                }
            }.toMap()
    }

    val entries: List<TechnicalEntry>
        get() = TechnicalFamily.entries.flatMap { family ->
            sections(family).flatMap { section -> sectionEntries(family, section) }
        }

    fun sections(family: TechnicalFamily): List<TechnicalSection> = when (family) {
        TechnicalFamily.VL80S -> listOf(
            TechnicalSection.PROFILES,
            TechnicalSection.EQUIPMENT,
            TechnicalSection.DIAGNOSTICS,
            TechnicalSection.ELECTRICAL,
            TechnicalSection.PNEUMATIC,
            TechnicalSection.ACCEPTANCE
        )
        TechnicalFamily.ERMAK -> listOf(
            TechnicalSection.PROFILES,
            TechnicalSection.SYSTEMS,
            TechnicalSection.EQUIPMENT,
            TechnicalSection.KNOWLEDGE,
            TechnicalSection.DIAGNOSTICS,
            TechnicalSection.ELECTRICAL,
            TechnicalSection.PNEUMATIC
        )
    }

    fun entries(family: TechnicalFamily, section: TechnicalSection, query: String = ""): List<TechnicalEntry> {
        val needle = query.trim().lowercase()
        return sectionEntries(family, section).filter { needle.isBlank() || needle in it.searchText }
    }

    fun entry(id: String): TechnicalEntry? {
        val canonicalId = legacyEquipmentIds[id.lowercase()] ?: id
        synchronized(sharedSectionCache) {
            sharedSectionCache.values.asSequence().flatten().firstOrNull { it.id == canonicalId }?.let { return it }
        }
        candidateSections(canonicalId).forEach { (family, section) ->
            sectionEntries(family, section).firstOrNull { it.id == canonicalId }?.let { return it }
        }
        return null
    }

    fun displayLines(lines: List<String>): List<String> = lines.mapNotNull { line ->
        line.split(" • ")
            .mapNotNull { part ->
                part.takeUnless { looksLikeEntryId(it) && entry(it) != null || isInternalTechnicalReference(it) }
                    ?.let(::technicalPresentationLine)?.takeIf(String::isNotBlank)
            }.distinct().joinToString(" • ").takeIf(String::isNotBlank)
    }

    fun referencedEntries(lines: List<String>): List<TechnicalEntry> =
        lines.flatMap { it.split(" • ") }.filter(::looksLikeEntryId).mapNotNull(::entry).distinctBy(TechnicalEntry::id)

    fun count(family: TechnicalFamily, section: TechnicalSection): Int = sectionEntries(family, section).size

    private fun sectionEntries(family: TechnicalFamily, section: TechnicalSection): List<TechnicalEntry> {
        val key = family to section
        synchronized(sharedSectionCache) { sharedSectionCache[key]?.let { return it } }
        val loaded = loadSection(family, section)
        synchronized(sharedSectionCache) { return sharedSectionCache.getOrPut(key) { loaded } }
    }

    private fun loadSection(family: TechnicalFamily, section: TechnicalSection): List<TechnicalEntry> = when (family) {
        TechnicalFamily.VL80S -> when (section) {
            TechnicalSection.PROFILES -> loadVl80sProfiles()
            TechnicalSection.EQUIPMENT -> loadVl80sEquipment()
            TechnicalSection.DIAGNOSTICS -> loadVl80sDiagnostics()
            TechnicalSection.ELECTRICAL -> loadVl80sElectrical()
            TechnicalSection.PNEUMATIC -> loadVl80sPneumatic()
            TechnicalSection.ACCEPTANCE -> loadVl80sAcceptance()
            TechnicalSection.KNOWLEDGE, TechnicalSection.SYSTEMS -> emptyList()
        }
        TechnicalFamily.ERMAK -> when (section) {
            TechnicalSection.PROFILES -> loadErmakProfiles()
            TechnicalSection.SYSTEMS -> loadErmakSystems()
            TechnicalSection.EQUIPMENT -> loadErmakEquipment()
            TechnicalSection.KNOWLEDGE -> loadErmakKnowledge()
            TechnicalSection.DIAGNOSTICS -> loadErmakDiagnostics()
            TechnicalSection.ELECTRICAL -> loadErmakSchemes().filter { it.section == TechnicalSection.ELECTRICAL }
            TechnicalSection.PNEUMATIC -> loadErmakSchemes().filter { it.section == TechnicalSection.PNEUMATIC }
            TechnicalSection.ACCEPTANCE -> emptyList()
        }
    }

    private fun candidateSections(id: String): List<Pair<TechnicalFamily, TechnicalSection>> = when {
        id.startsWith("VL80-ACC-") || id.startsWith("VL80-ROUTE-") || id.startsWith("route_") -> listOf(TechnicalFamily.VL80S to TechnicalSection.ACCEPTANCE)
        id.startsWith("VL-EQ-") -> listOf(TechnicalFamily.VL80S to TechnicalSection.EQUIPMENT)
        id.startsWith("VL-SCH-") -> listOf(TechnicalFamily.VL80S to TechnicalSection.ELECTRICAL, TechnicalFamily.VL80S to TechnicalSection.PNEUMATIC)
        id.startsWith("ER-VARIANT-") -> listOf(TechnicalFamily.ERMAK to TechnicalSection.PROFILES)
        id.startsWith("SYS-") -> listOf(TechnicalFamily.ERMAK to TechnicalSection.SYSTEMS)
        id.startsWith("ER-EQ-") -> listOf(TechnicalFamily.ERMAK to TechnicalSection.EQUIPMENT)
        id.startsWith("ER-KB-") -> listOf(TechnicalFamily.ERMAK to TechnicalSection.KNOWLEDGE)
        id.startsWith("ER-DIAG-") -> listOf(TechnicalFamily.ERMAK to TechnicalSection.DIAGNOSTICS)
        id.startsWith("ER-SCH-") -> listOf(TechnicalFamily.ERMAK to TechnicalSection.ELECTRICAL, TechnicalFamily.ERMAK to TechnicalSection.PNEUMATIC)
        id.matches(Regex("^[a-z][a-z0-9]+(?:-[a-z0-9]+)+$")) -> listOf(TechnicalFamily.VL80S to TechnicalSection.DIAGNOSTICS)
        else -> emptyList()
    }

    private fun looksLikeEntryId(value: String): Boolean {
        val id=value.trim()
        return id.startsWith("VL-") || id.startsWith("VL80-") || id.startsWith("ER-") || id.startsWith("SYS-") || id.startsWith("route_") || id.matches(Regex("^[a-z][a-z0-9]+(?:-[a-z0-9]+)+$"))
    }

    private fun json(asset: String): JSONObject =
        context.assets.open("$asset.gz").use { input ->
            GZIPInputStream(input).bufferedReader().use { reader -> JSONObject(reader.readText()) }
        }

    private fun loadVl80sProfiles(): List<TechnicalEntry> {
        val root = json("technical/vl80s_variants.json")
        return root.array("physicalBuckets").objects().map { item ->
            entry(
                item, TechnicalFamily.VL80S, TechnicalSection.PROFILES,
                title = item.optString("range"),
                subtitle = "Профиль секции ${item.optString("id")}",
                status = item.optString("confidence"),
                blocks = listOfNotEmpty(
                    block("Диапазон", item.optString("range")),
                    block("Особенности", item.array("features").strings()),
                    block("Опорные источники", item.array("sourceTags").strings())
                )
            )
        }
    }

    private fun loadErmakProfiles(): List<TechnicalEntry> {
        val selector = json("technical/ermak_schemes.json").obj("variantSelector")
        return selector.array("dimensions").objects().map { item ->
            val id = "ER-VARIANT-${item.optString("id")}"
            val source = JSONObject(item.toString()).put("id", id)
            entry(
                source, TechnicalFamily.ERMAK, TechnicalSection.PROFILES,
                title = item.optString("title"),
                subtitle = item.array("values").strings().joinToString(" • "),
                status = if (item.optBoolean("required")) "REQUIRED" else "PROFILE_REQUIRED",
                blocks = listOfNotEmpty(
                    block("Доступные значения", item.array("values").strings())
                )
            )
        }
    }

    private fun loadVl80sEquipment(): List<TechnicalEntry> =
        json("technical/vl80s_equipment.json").array("records").objects().map { item ->
            val related = buildList {
                addAll(item.array("diagnosticScenarioIds").strings())
                addAll(item.array("relations").objects().mapNotNull { it.optString("targetId").takeIf(String::isNotBlank) })
            }.distinct()
            entry(
                item, TechnicalFamily.VL80S, TechnicalSection.EQUIPMENT,
                title = item.optString("name"),
                subtitle = item.optString("purpose"),
                status = item.optString("evidenceStatus"),
                blocks = listOfNotEmpty(
                    block("Назначение", item.optString("purpose")),
                    block("Расположение", item.obj("location").summary()),
                    block("Обозначения", item.array("aliases").strings() + item.array("schemeNodeIds").strings()),
                    block("Системы", item.array("systemIds").strings()),
                    block("Диагностика", item.array("diagnosticScenarioIds").strings()),
                    block("Источники", item.array("sourceRefs").stringsOrSummaries())
                ),
                relatedIds = related
            )
        }

    private fun loadVl80sAcceptance(): List<TechnicalEntry> {
        val root = json("technical/vl80s_acceptance.json")
        val items = root.array("items").objects().map { item ->
            entry(
                item, TechnicalFamily.VL80S, TechnicalSection.ACCEPTANCE,
                title = item.optString("title"), subtitle = item.optString("check"), status = item.optString("phase"),
                blocks = listOfNotEmpty(
                    block("Проверка", item.optString("check")),
                    block("Нормальные признаки", item.array("normalSigns").strings()),
                    block("Возможные неисправности", item.array("possibleFaults").strings()),
                    block("Опасные признаки", item.array("dangerFlags").strings()),
                    block("Диагностические переходы", item.array("diagnosticHints").stringsOrSummaries()),
                    block("Граница действий", item.optString("actionBoundary")),
                    block("Условия", item.array("preconditions").strings()),
                    block("Применимость", item.obj("variantRule").summary()),
                    block("Источники", item.array("sourceRefs").stringsOrSummaries())
                ),
                relatedIds = buildList {
                    item.optString("equipmentId").takeIf(String::isNotBlank)?.let(::add)
                    addAll(item.array("relatedEquipmentIds").strings())
                    addAll(item.array("diagnosticHints").objects().mapNotNull { hint -> hint.optString("scenarioId").takeIf(String::isNotBlank) ?: hint.optString("id").takeIf(String::isNotBlank) })
                }.distinct()
            )
        }
        val routes = root.array("routes").objects().map { route ->
            val ids=route.array("itemIds").strings()
            val mode=when(route.optString("mode")){"step_by_step"->"пошагово";"checklist"->"контрольный список";"route"->"маршрут";"area"->"по зоне";else->"маршрут"}
            TechnicalEntry(
                id="VL80-ROUTE-${route.optString("id")}", family=TechnicalFamily.VL80S, section=TechnicalSection.ACCEPTANCE,
                title=route.optString("title"), subtitle="${ids.size} пунктов • $mode", status="ROUTE",
                blocks=listOf(TechnicalBlock("Режим",listOf("Последовательное прохождение пунктов приёмки с отметками «проверено» и «замечание»."))),
                sequence=ids, searchText=(route.optString("title")+" "+mode).lowercase()
            )
        }
        val effectiveRoutes=if(routes.isNotEmpty()) routes else listOf(TechnicalEntry(
            id="VL80-ROUTE-fallback", family=TechnicalFamily.VL80S, section=TechnicalSection.ACCEPTANCE,
            title="Полная приёмка", subtitle="${items.size} пунктов • пошагово", status="ROUTE", blocks=emptyList(),
            sequence=items.map(TechnicalEntry::id), searchText="полная приёмка пошагово"
        ))
        return effectiveRoutes + items
    }

    private fun loadVl80sElectrical(): List<TechnicalEntry> =
        json("technical/vl80s_electrical.json").array("baseSchemes").objects().map { item ->
            schemeEntry(item, TechnicalFamily.VL80S, TechnicalSection.ELECTRICAL, "semanticEdges")
        }

    private fun loadVl80sPneumatic(): List<TechnicalEntry> =
        json("technical/vl80s_pneumatic.json").array("views").objects().map { item ->
            schemeEntry(item, TechnicalFamily.VL80S, TechnicalSection.PNEUMATIC, "edges")
        }

    private fun loadVl80sDiagnostics(): List<TechnicalEntry> =
        json("technical/vl80s_diagnostics.json").array("scenarios").objects().map { item ->
            entry(
                item, TechnicalFamily.VL80S, TechnicalSection.DIAGNOSTICS,
                title = item.optString("title"),
                subtitle = item.optString("summary"),
                status = item.optString("severity"),
                blocks = listOfNotEmpty(
                    block("Симптом", item.optString("summary")),
                    block("Категория", item.optString("category")),
                    block("Связанное оборудование", item.array("equipmentIds").strings()),
                    block("Системы", item.array("systemIds").strings()),
                    block("Приёмка", item.obj("acceptance").summary()),
                    block("Электросхемы", item.obj("electricalSchemes").summary()),
                    block("Пневмосхемы", item.obj("pneumaticViews").summary()),
                    block("Применимость", item.array("applicableVariantIds").strings())
                ),
                relatedIds = buildList {
                    addAll(item.array("relatedScenarioIds").strings())
                    addAll(item.array("equipmentIds").strings())
                    addAll(item.obj("acceptance").allStrings())
                    addAll(item.obj("electricalSchemes").allStrings())
                    addAll(item.obj("pneumaticViews").allStrings())
                }.distinct()
            )
        }

    private fun loadErmakSystems(): List<TechnicalEntry> =
        json("technical/ermak_system_map.json").array("systems").objects().map { item ->
            entry(
                item, TechnicalFamily.ERMAK, TechnicalSection.SYSTEMS,
                title = item.optString("name").ifBlank { item.optString("title") },
                subtitle = item.optString("purpose").ifBlank { item.optString("description") },
                status = item.optString("evidenceStatus").ifBlank { item.optString("status") },
                blocks = listOfNotEmpty(
                    block("Назначение", item.optString("purpose").ifBlank { item.optString("description") }),
                    block("Состав", item.array("components").strings() + item.array("equipmentRefs").strings()),
                    block("Входы", item.array("inputs").strings()),
                    block("Выходы", item.array("outputs").strings()),
                    block("Защиты", item.array("protections").strings()),
                    block("Связи", item.array("relations").stringsOrSummaries()),
                    block("Источники", item.array("sourceRefs").stringsOrSummaries())
                ),
                relatedIds = item.array("equipmentRefs").strings() +
                    item.array("relations").objects().mapNotNull { it.optString("targetId").takeIf(String::isNotBlank) }
            )
        }

    private fun loadErmakEquipment(): List<TechnicalEntry> =
        json("technical/ermak_equipment.json").array("records").objects().map { item ->
            entry(
                item, TechnicalFamily.ERMAK, TechnicalSection.EQUIPMENT,
                title = item.optString("name"),
                subtitle = item.optString("purpose"),
                status = item.optString("evidenceStatus"),
                blocks = listOfNotEmpty(
                    block("Назначение", item.optString("purpose")),
                    block("Расположение", item.obj("location").summary()),
                    block("Количество", item.optString("quantity")),
                    block("Обозначения", item.array("schemeDesignations").strings() + item.array("modelNames").strings()),
                    block("Параметры", item.array("parameters").stringsOrSummaries()),
                    block("Применимость", item.obj("applicability").summary()),
                    block("Системы", item.array("systemIds").strings()),
                    block("Источники", item.array("sourceRefs").stringsOrSummaries())
                ),
                relatedIds = item.array("relations").objects().mapNotNull { it.optString("targetId").takeIf(String::isNotBlank) } +
                    item.array("systemIds").strings()
            )
        }

    private fun loadErmakKnowledge(): List<TechnicalEntry> =
        json("technical/ermak_knowledge.json").array("articles").objects().map { item ->
            entry(
                item, TechnicalFamily.ERMAK, TechnicalSection.KNOWLEDGE,
                title = item.optString("title"),
                subtitle = item.optString("summary"),
                status = item.optString("status"),
                blocks = listOfNotEmpty(
                    block("Кратко", item.optString("summary")),
                    block("Расположение", item.optString("locationScope")),
                    block("Принцип работы", item.optString("principle")),
                    block("Нормальное состояние", item.array("normalState").strings()),
                    block("Признаки отклонения", item.array("deviationSigns").strings()),
                    block("Параметры", item.array("keyParameters").stringsOrSummaries()),
                    block("Применимость", item.obj("scope").summary()),
                    block("Особенности исполнения", item.array("variantRules").strings()),
                    block("Источники", item.array("sourceRefs").stringsOrSummaries())
                ),
                relatedIds = buildList {
                    addAll(item.array("equipmentRefs").strings())
                    addAll(item.array("relatedSystems").strings())
                    addAll(item.obj("futureLinks").allStrings())
                }.distinct()
            )
        }

    private fun loadErmakDiagnostics(): List<TechnicalEntry> =
        json("technical/ermak_diagnostics.json").array("scenarios").objects().map { item ->
            val projection = item.obj("vl80sUiProjection")
            entry(
                item, TechnicalFamily.ERMAK, TechnicalSection.DIAGNOSTICS,
                title = item.optString("title"),
                subtitle = item.optString("symptom"),
                status = item.optString("severity").ifBlank { item.optString("status") },
                blocks = listOfNotEmpty(
                    block("Симптом", item.optString("symptom")),
                    block("Немедленные действия", projection.array("immediateActions").strings()),
                    block("Опасные признаки", projection.array("dangerSigns").strings()),
                    block("Уточнения", projection.array("questions").stringsOrSummaries()),
                    block("Возможные причины", projection.array("probableCauses").strings()),
                    block("Безопасные проверки", projection.array("checks").stringsOrSummaries()),
                    block("Запрещено", projection.array("prohibited").strings()),
                    block("Условия прекращения", projection.array("stopConditions").strings()),
                    block("Что доложить", projection.array("reportFields").strings()),
                    block("Применимость", projection.optString("applicability")),
                    block("Источник", projection.optString("sourceNote"))
                ),
                relatedIds = buildList {
                    addAll(item.array("systemIds").strings())
                    addAll(item.array("equipmentRefs").strings())
                    addAll(item.array("knowledgeRefs").strings())
                    addAll(item.array("relatedScenarioIds").strings())
                }.distinct()
            )
        }

    private fun loadErmakSchemes(): List<TechnicalEntry> =
        json("technical/ermak_schemes.json").array("schemes").objects().map { item ->
            val type = item.optString("schemeType")
            val section = if (type.contains("pneumatic") || type.contains("brake")) {
                TechnicalSection.PNEUMATIC
            } else {
                TechnicalSection.ELECTRICAL
            }
            val hotspots = item.obj("layers").array("hotspotLayer").objects()
            val flow = item.obj("layers").array("flowLayer").objects()
            val sequence = if (flow.isNotEmpty()) {
                flow.mapNotNull { step ->
                    step.optString("to").takeIf(String::isNotBlank)
                        ?: step.optString("equipmentId").takeIf(String::isNotBlank)
                }
            } else hotspots.mapNotNull { it.optString("equipmentId").takeIf(String::isNotBlank) }
            entry(
                item, TechnicalFamily.ERMAK, section,
                title = item.optString("title"),
                subtitle = item.optString("representationMode").replace('_', ' '),
                status = item.optString("status"),
                blocks = listOfNotEmpty(
                    block("Тип", type),
                    block("Системы", item.array("systemIds").strings()),
                    block("Применимость", item.obj("variantRules").summary()),
                    block("Оборудование", item.array("equipmentRefs").strings()),
                    block("Источники", item.array("sourceRefs").stringsOrSummaries()),
                    block("Покрытие", item.obj("coverage").summary())
                ),
                relatedIds = item.array("equipmentRefs").strings(),
                sequence = sequence.distinct()
            )
        }

    private fun schemeEntry(
        item: JSONObject,
        family: TechnicalFamily,
        section: TechnicalSection,
        edgeKey: String
    ): TechnicalEntry {
        val nodes = item.array("nodes").objects()
        val sequence = nodes.mapNotNull { node ->
            node.optString("equipmentId").takeIf(String::isNotBlank)
                ?: node.optString("virtualNodeId").takeIf(String::isNotBlank)
        }
        return entry(
            item, family, section,
            title = item.optString("title"),
            subtitle = item.optString("scopeNote"),
            status = item.optString("status"),
            blocks = listOfNotEmpty(
                block("Область", item.optString("scopeNote")),
                block("Профили", item.array("profiles").strings()),
                block("Системы", item.array("systems").strings()),
                block("Узлы", nodes.map { node ->
                    listOf(node.optString("label"), node.optString("equipmentId").ifBlank { node.optString("virtualNodeId") })
                        .filter(String::isNotBlank).joinToString(" — ")
                }),
                block("Связи", item.array(edgeKey).stringsOrSummaries()),
                block("Источники", item.array("sourceRefs").stringsOrSummaries())
            ),
            relatedIds = sequence,
            sequence = sequence
        )
    }

    private fun entry(
        source: JSONObject,
        family: TechnicalFamily,
        section: TechnicalSection,
        title: String,
        subtitle: String,
        status: String,
        blocks: List<TechnicalBlock>,
        relatedIds: List<String> = emptyList(),
        sequence: List<String> = emptyList()
    ): TechnicalEntry {
        val id = source.optString("id")
        val aliases = source.array("aliases").strings()
        val search = buildList {
            add(title); add(subtitle); addAll(aliases)
            blocks.forEach { block ->
                add(block.title)
                addAll(block.lines.flatMap { it.split(" • ") }
                    .mapNotNull(::technicalPresentationLine))
            }
        }.joinToString(" ").lowercase()
        return TechnicalEntry(
            id = id,
            family = family,
            section = section,
            title = title.ifBlank { id },
            subtitle = subtitle,
            status = status,
            blocks = blocks,
            relatedIds = relatedIds.filter(String::isNotBlank).distinct(),
            sequence = sequence.filter(String::isNotBlank).distinct(),
            searchText = search
        )
    }
}

private fun JSONObject.array(key: String): JSONArray = optJSONArray(key) ?: JSONArray()
private fun JSONObject.obj(key: String): JSONObject = optJSONObject(key) ?: JSONObject()

private fun JSONArray.objects(): List<JSONObject> = buildList {
    for (index in 0 until length()) optJSONObject(index)?.let(::add)
}

private fun JSONArray.strings(): List<String> = buildList {
    for (index in 0 until length()) {
        val value = opt(index)
        if (value is String && value.isNotBlank()) add(value)
    }
}

private fun JSONArray.stringsOrSummaries(): List<String> = buildList {
    for (index in 0 until length()) {
        when (val value = opt(index)) {
            is String -> if (value.isNotBlank()) add(value)
            is JSONObject -> value.summary().takeIf(String::isNotBlank)?.let(::add)
        }
    }
}

private fun JSONObject.summary(): String = buildList {
    val preferred = listOf(
        "title", "name", "summary", "text", "prompt", "action", "expected", "ifAbnormal",
        "value", "unit", "type", "relationType", "kind", "targetId", "sourceId", "locator",
        "zone", "sectionScope", "confidence", "models", "profiles", "includeProfiles", "excludeProfiles"
    )
    preferred.forEach { key ->
        when (val value = opt(key)) {
            is String -> if (value.isNotBlank()) add(value)
            is Number, is Boolean -> add(value.toString())
            is JSONArray -> addAll(value.strings())
        }
    }
    if (isEmpty()) addAll(allStrings().take(8))
}.distinct().joinToString(" • ")

private fun JSONObject.allStrings(): List<String> = buildList {
    val keys = keys()
    while (keys.hasNext()) {
        when (val value = opt(keys.next())) {
            is String -> if (value.isNotBlank()) add(value)
            is JSONArray -> {
                addAll(value.strings())
                value.objects().forEach { addAll(it.allStrings()) }
            }
            is JSONObject -> addAll(value.allStrings())
        }
    }
}

private fun block(title: String, line: String): TechnicalBlock? =
    line.takeIf(String::isNotBlank)?.let { TechnicalBlock(title, listOf(it)) }

private fun block(title: String, lines: List<String>): TechnicalBlock? =
    lines.filter(String::isNotBlank).distinct().takeIf { it.isNotEmpty() }?.let { TechnicalBlock(title, it) }

private fun listOfNotEmpty(vararg blocks: TechnicalBlock?): List<TechnicalBlock> = blocks.filterNotNull()

internal fun isInternalTechnicalReference(value: String): Boolean =
    value.trim().matches(Regex("^(?:VL80|VL|ER)-[A-Z0-9][A-Z0-9_-]*$", RegexOption.IGNORE_CASE))

internal fun userFacingTechnicalText(value: String): String = value
    .replace(Regex("variant-profile", RegexOption.IGNORE_CASE), "профиль исполнения")
    .replace(Regex("ER-EQ/KB\\s+карточки", RegexOption.IGNORE_CASE), "карточки оборудования и справочные материалы")
    .replace(Regex("ER-EQ/KB", RegexOption.IGNORE_CASE), "карточки оборудования и справочные материалы")
    .replace(Regex("\\b(?:VL80|VL|ER)-[A-Z0-9][A-Z0-9_-]*\\b", RegexOption.IGNORE_CASE), "")
    .replace(Regex("\\s{2,}"), " ")
    .replace(Regex("(?:\\s*•\\s*){2,}"), " • ")
    .trim(' ', '•')
