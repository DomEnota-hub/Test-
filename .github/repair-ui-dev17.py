from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

PRESENTATION = r'''package ru.railbrake.calculator.core

private val hiddenMetadata = setOf(
    "confirmed_general",
    "base_confirmed",
    "evolution_confirmed",
    "training_source_confirmed",
    "mixed_source_confirmed",
    "secondary_source_requires_drawing",
    "historical_technical_source",
    "open_exact_numbers",
    "final",
    "pass"
)

private val exactLabels = mapOf(
    "local_procedure" to "Действовать по местной инструкции и установленному технологическому процессу",
    "section-aware applicability" to "Применимость определяется по фактическому исполнению секции",
    "unknown" to "Не определено",
    "head" to "Головная секция",
    "booster" to "Бустерная секция",
    "2es5k" to "2ЭС5К",
    "3es5k" to "3ЭС5К",
    "group_base" to "Групповое регулирование",
    "axle_control_modern" to "Современное поосное регулирование",
    "3es5k_434_experimental" to "3ЭС5К №434, опытное исполнение",
    "3es5k_896plus" to "3ЭС5К №896 и позднее",
    "3es5k_axle_control_pre896" to "3ЭС5К с поосным регулированием, до №896",
    "3es5k_base_early" to "3ЭС5К, базовое раннее исполнение",
    "3es5k_rolling_bearing" to "3ЭС5К с подшипниками качения",
    "2es5k_base_early" to "2ЭС5К, базовое раннее исполнение",
    "2es5k_rolling_bearing" to "2ЭС5К с подшипниками качения",
    "2es5k_axle_control_modern" to "2ЭС5К с современным поосным регулированием",
    "klub-u_saut-tskbm" to "КЛУБ-У / САУТ / ТСКБМ",
    "blok_2es5k" to "БЛОК (2ЭС5К)",
    "plain" to "Подшипник скольжения",
    "rolling" to "Подшипник качения",
    "vl80s_unknown" to "Исполнение не определено",
    "vl80s_pre697" to "До №697",
    "vl80s_697_1260" to "№697–1260",
    "vl80s_1261_1405" to "№1261–1405",
    "vl80s_1406_2318" to "№1406–2318",
    "vl80s_2319_2348" to "№2319–2348",
    "vl80s_2349_2653" to "№2349–2653",
    "vl80s_2654plus" to "№2654 и позднее",
    "vl80s_modified_or_mixed" to "Модернизированная или рекомплектованная секция",
    "early_sme" to "Раннее исполнение СМЕ",
    "sme_three_section_capable" to "Допускается трёхсекционная СМЕ",
    "third_section_rheostatic_brake_unavailable" to "Реостатное торможение третьей секции не используется",
    "pr_isolation_on_working_positions" to "Изоляция ПР на рабочих позициях",
    "ekg_sme_sync_updated" to "Изменённая синхронизация ЭКГ при СМЕ",
    "burt16" to "БУРТ-16",
    "vu_protection_rp21_22" to "Защита ВУ с РП21/РП22",
    "aux_compressor_pvu7" to "Вспомогательный компрессор / ПВУ7",
    "fr_late_scheme" to "Поздняя схема фазорасщепителя",
    "fire_signalization_present" to "Установлена пожарная сигнализация",
    "no_exact_scheme_assumption" to "Точную схему нельзя определять без подтверждения исполнения",
    "actual_section_drawing_has_priority" to "Приоритет имеет фактическая схема конкретной секции",
    "serial_number_not_sufficient" to "Номера локомотива недостаточно для определения комплектации",
    "do_not_merge_vl80sk_into_base_vl80s" to "ВЛ80СК не объединяется с базовым ВЛ80С",
    "do_not_assume_same_variant_for_recombined_sections" to "Для рекомплектованных секций исполнение определяется отдельно",
    "do_not_use_training_scheme_as_universal_mounting_scheme" to "Учебная схема не считается универсальной монтажной схемой",
    "exact_wire_apparatus_setting_requires_section_specific_source" to "Точные провода и аппараты требуют источника по конкретной секции"
)

private val englishRules = mapOf(
    "variant selection happens before detailed scheme rendering. unknown dimensions do not silently default to another execution." to
        "Сначала выбирается исполнение, затем строится подробная схема. Неизвестные параметры не подменяются другим исполнением автоматически.",
    "booster is only valid for 3es5k" to "Бустерная секция применяется только для 3ЭС5К.",
    "3es5k_434_experimental and 3es5k_896plus are separate layouts" to
        "Опытный 3ЭС5К №434 и 3ЭС5К №896 и позднее имеют отдельные компоновки.",
    "brake variants 395 / 130 / 130-2 are mutually exclusive for one selected execution" to
        "Для выбранного исполнения используется один вариант тормозного оборудования: №395, №130 или №130-2.",
    "motor-axle bearing plain / rolling are separate scheme profiles" to
        "Исполнения с моторно-осевыми подшипниками скольжения и качения рассматриваются раздельно.",
    "blok scheme is not generalized to all 2es5k or to 3es5k" to
        "Схема БЛОК не распространяется автоматически на все 2ЭС5К и на 3ЭС5К.",
    "msud-015/axle-control overlays do not replace the base wiring diagram unless a full primary late-execution scheme is available" to
        "Наложения МСУД-015 и поосного регулирования не заменяют базовую электрическую схему без полного первичного источника для позднего исполнения."
)

private val legacyIdentifier = Regex("^[a-z][A-Za-z0-9_-]{1,}$")
private val sourcePoint = Regex("^p\\.(\\d+(?:[.-]\\d+)*)$", RegexOption.IGNORE_CASE)
private val vlProfileSubtitle = Regex("^Профиль секции\\s+vl80s_[A-Za-z0-9_]+$", RegexOption.IGNORE_CASE)
private val rangeTitle = Regex("^(\\d+)-(\\d+)$")

internal fun technicalPresentationLine(value: String): String? {
    val cleaned = userFacingTechnicalText(value).trim()
    if (cleaned.isBlank()) return null
    val parts = cleaned.split(" • ")
        .mapNotNull(::technicalPresentationAtom)
        .distinct()
    return parts.joinToString(" • ").takeIf(String::isNotBlank)
}

private fun technicalPresentationAtom(value: String): String? {
    val raw = value.trim()
    if (raw.isBlank() || isInternalTechnicalReference(raw)) return null
    val key = raw.lowercase()
    if (key in hiddenMetadata) return null
    exactLabels[key]?.let { return it }
    englishRules[key]?.let { return it }
    sourcePoint.matchEntire(raw)?.let { return "п. ${it.groupValues[1]}" }
    if (vlProfileSubtitle.matches(raw)) return "Профиль исполнения секции ВЛ80С"
    if (looksLikeInternalEnglishRule(raw)) return "Ограничение применяется по выбранному исполнению."
    if (legacyIdentifier.matches(raw)) return null
    if (raw.contains('_') && raw.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == ' ' }) return null
    return raw
}

private fun looksLikeInternalEnglishRule(value: String): Boolean {
    val lower = value.lowercase()
    if (!lower.any { it in 'a'..'z' }) return false
    val markers = listOf(" scheme", "variant ", " profile", " layout", " default", " is only ", " are separate", " selection ", " unless ")
    return markers.any(lower::contains)
}

internal fun technicalEntryTitle(entry: TechnicalEntry): String {
    if (entry.family == TechnicalFamily.VL80S && entry.section == TechnicalSection.PROFILES) {
        val raw = entry.title.trim()
        return when {
            raw.equals("unknown", true) -> "Исполнение не определено"
            raw == "<697" -> "До №697"
            raw == ">=2654" -> "№2654 и позднее"
            rangeTitle.matches(raw) -> rangeTitle.matchEntire(raw)!!.let { "№${it.groupValues[1]}–${it.groupValues[2]}" }
            else -> technicalPresentationLine(raw) ?: "Профиль исполнения ВЛ80С"
        }
    }
    return technicalPresentationLine(entry.title) ?: "Материал"
}

internal fun technicalEntrySubtitle(entry: TechnicalEntry): String? {
    if (entry.family == TechnicalFamily.VL80S && entry.section == TechnicalSection.PROFILES) {
        return "Профиль исполнения секции ВЛ80С"
    }
    return technicalPresentationLine(entry.subtitle)
}

internal fun technicalStatusPresentation(status: String): String? = when (status.trim().uppercase()) {
    "INFORMATION" -> "Справочно"
    "ATTENTION" -> "Внимание"
    "RESTRICT_OPERATION" -> "Ограничить эксплуатацию"
    "STOP_AND_REPORT" -> "Остановиться и доложить"
    "REQUIRED" -> "Обязательный параметр"
    "PROFILE_REQUIRED" -> "Требуется выбрать исполнение"
    "CONFLICT" -> "Требует уточнения"
    "FALLBACK" -> "Исполнение не определено"
    "CONFIRMED_GENERAL", "BASE_CONFIRMED", "CONFIRMED" -> "Подтверждено"
    "EVOLUTION_CONFIRMED" -> "Подтверждено для указанного исполнения"
    "TRAINING_SOURCE_CONFIRMED" -> "Подтверждено учебным источником"
    "MIXED_SOURCE_CONFIRMED" -> "Подтверждено несколькими источниками"
    "SECONDARY_SOURCE_REQUIRES_DRAWING" -> "Требует сверки со схемой секции"
    "OPEN", "OPEN_EXACT_NUMBERS" -> "Требует уточнения"
    "FINAL" -> "Проверено"
    else -> null
}
'''

TECH_SCREEN = r'''package ru.railbrake.calculator.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.railbrake.calculator.core.TechnicalDataRepository
import ru.railbrake.calculator.core.TechnicalEntry
import ru.railbrake.calculator.core.TechnicalFamily
import ru.railbrake.calculator.core.TechnicalSection
import ru.railbrake.calculator.core.technicalEntrySubtitle
import ru.railbrake.calculator.core.technicalEntryTitle
import ru.railbrake.calculator.core.technicalPresentationLine
import ru.railbrake.calculator.core.technicalStatusPresentation

@Composable
fun TechnicalCatalogScreen(
    initialFamily: TechnicalFamily = TechnicalFamily.VL80S,
    initialSection: TechnicalSection = TechnicalSection.EQUIPMENT,
    sectionBackLabel: String = "Локомотивы / атлас",
    onSectionBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { TechnicalDataRepository(context.applicationContext) }
    var familyName by rememberSaveable { mutableStateOf(initialFamily.name) }
    var sectionName by rememberSaveable { mutableStateOf(initialSection.name) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val family = runCatching { TechnicalFamily.valueOf(familyName) }.getOrDefault(TechnicalFamily.VL80S)
    val availableSections = remember(family) { repository.sections(family) }
    val selectedSection = runCatching { TechnicalSection.valueOf(sectionName) }.getOrNull()?.takeIf(availableSections::contains)
        ?: availableSections.first()
    val selected = selectedId?.let(repository::entry)

    BackHandler(enabled = selected != null) { selectedId = null }

    if (selected != null) {
        TechnicalEntryDetail(
            entry = selected,
            repository = repository,
            onBack = { selectedId = null },
            onOpen = { target -> selectedId = target.id }
        )
        return
    }

    val visible = remember(family, selectedSection, query) {
        repository.entries(family, selectedSection, query)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ChildBackButton(sectionBackLabel, onSectionBack)
            RailSectionHeader(
                "Техническая база ${family.title}",
                "Материалы и связи между разделами"
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(TechnicalFamily.VL80S, TechnicalFamily.ERMAK)) { option ->
                    FilterChip(
                        selected = family == option,
                        onClick = {
                            familyName = option.name
                            val sections = repository.sections(option)
                            if (runCatching { TechnicalSection.valueOf(sectionName) }.getOrNull() !in sections) sectionName = sections.first().name
                            query = ""
                        },
                        label = { Text(option.title) }
                    )
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(availableSections) { option ->
                    val accent = technicalSectionAccent(option, "")
                    FilterChip(
                        selected = selectedSection == option,
                        onClick = { sectionName = option.name; query = "" },
                        label = {
                            Text(
                                "${option.title} · ${repository.count(family, option)}",
                                color = if (selectedSection == option) accent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Поиск по названию, аппарату или признаку") },
                shape = RoundedCornerShape(16.dp)
            )
        }
        item {
            Text(
                "${selectedSection.title}: ${visible.size}",
                style = MaterialTheme.typography.labelLarge,
                color = technicalSectionAccent(selectedSection, ""),
                fontWeight = FontWeight.Bold
            )
        }
        if (visible.isEmpty()) {
            item {
                InfoCard(
                    "Ничего не найдено",
                    listOf("Измените запрос или выберите другой раздел."),
                    MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
        items(visible, key = { it.id }) { entry ->
            val accent = technicalSectionAccent(entry.section, entry.status)
            Card(
                onClick = { selectedId = entry.id },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.52f))
            ) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    technicalStatusLabel(entry.status)?.let { status ->
                        Text(status, style = MaterialTheme.typography.labelSmall, color = accent)
                    }
                    Text(technicalEntryTitle(entry), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    technicalEntrySubtitle(entry)?.let { subtitle ->
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Открыть карточку →", color = accent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TechnicalEntryDetail(
    entry: TechnicalEntry,
    repository: TechnicalDataRepository,
    onBack: () -> Unit,
    onOpen: (TechnicalEntry) -> Unit
) {
    val accent = technicalSectionAccent(entry.section, entry.status)
    val referencedIds = entry.blocks
        .flatMap { repository.referencedEntries(it.lines) }
        .map { it.id }
        .toSet()
    val relatedEntries = entry.relatedIds
        .distinct()
        .mapNotNull(repository::entry)
        .filterNot { it.id in referencedIds }
        .take(40)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ChildBackButton(entry.section.title, onBack)
            Text(technicalEntryTitle(entry), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            technicalEntrySubtitle(entry)?.let { subtitle ->
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            technicalStatusLabel(entry.status)?.let { status ->
                RailStatusPill(status, accent = accent)
            }
        }
        if (entry.sequence.isNotEmpty()) {
            item { TechnicalSequence(entry, repository, onOpen) }
        }
        items(entry.blocks, key = { it.title }) { block ->
            val displayLines = repository.displayLines(block.lines)
                .mapNotNull(::technicalPresentationLine)
                .distinct()
            val references = repository.referencedEntries(block.lines)
            if (displayLines.isEmpty() && references.isEmpty()) return@items
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.38f)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(block.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    displayLines.forEach { line -> Text("• $line") }
                    references.forEach { target ->
                        OutlinedButton(
                            onClick = { onOpen(target) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("${technicalEntryTitle(target)} →") }
                    }
                }
            }
        }
        if (relatedEntries.isNotEmpty()) {
            item {
                HorizontalDivider()
                Text("Связанные материалы", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            }
            items(relatedEntries, key = { "related-${it.id}" }) { target ->
                OutlinedButton(
                    onClick = { onOpen(target) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("${technicalEntryTitle(target)} →")
                }
            }
        }
    }
}

@Composable
private fun TechnicalSequence(
    entry: TechnicalEntry,
    repository: TechnicalDataRepository,
    onOpen: (TechnicalEntry) -> Unit
) {
    var step by rememberSaveable(entry.id) { mutableIntStateOf(0) }
    val currentId = entry.sequence[step.coerceIn(entry.sequence.indices)]
    val target = repository.entry(currentId)
    val accent = technicalSectionAccent(entry.section, entry.status)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Пошаговая цепь", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text("Шаг ${step + 1} из ${entry.sequence.size}", color = accent)
            Text(target?.let(::technicalEntryTitle) ?: "Элемент цепи", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { if (step > 0) step-- }, enabled = step > 0) { Text("Назад") }
                Button(onClick = { if (step < entry.sequence.lastIndex) step++ }, enabled = step < entry.sequence.lastIndex) { Text("Далее") }
            }
            if (target != null) {
                OutlinedButton(onClick = { onOpen(target) }) { Text("Карточка оборудования") }
            }
        }
    }
}

@Composable
private fun technicalSectionAccent(section: TechnicalSection, status: String): Color {
    if (status.equals("STOP_AND_REPORT", true) || status.equals("RESTRICT_OPERATION", true)) {
        return MaterialTheme.colorScheme.error
    }
    return when (section) {
        TechnicalSection.PROFILES -> MaterialTheme.colorScheme.tertiary
        TechnicalSection.SYSTEMS -> MaterialTheme.colorScheme.secondary
        TechnicalSection.EQUIPMENT -> MaterialTheme.colorScheme.primary
        TechnicalSection.KNOWLEDGE -> MaterialTheme.colorScheme.tertiary
        TechnicalSection.DIAGNOSTICS -> MaterialTheme.colorScheme.secondary
        TechnicalSection.ELECTRICAL -> MaterialTheme.colorScheme.tertiary
        TechnicalSection.PNEUMATIC -> MaterialTheme.colorScheme.primary
        TechnicalSection.ACCEPTANCE -> MaterialTheme.colorScheme.primary
    }
}

internal fun technicalStatusLabel(status: String): String? = technicalStatusPresentation(status)
'''

TESTS = r'''package ru.railbrake.calculator.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechnicalPresentationTest {
    @Test
    fun internalReferencesAreRecognized() {
        assertTrue(isInternalTechnicalReference("VL-EQ-SF-001"))
        assertTrue(isInternalTechnicalReference("VL80-ACC-005"))
        assertTrue(isInternalTechnicalReference("ER-EQ-014"))
        assertFalse(isInternalTechnicalReference("ЭПК и контроль бдительности"))
    }

    @Test
    fun embeddedTechnicalTermsAreHumanized() {
        assertEquals(
            "Проверить выбранный профиль исполнения и открыть связанные карточки оборудования и справочные материалы.",
            userFacingTechnicalText("Проверить выбранный variant-profile и открыть связанные ER-EQ/KB карточки.")
        )
        assertEquals("Проверить связанную схему", userFacingTechnicalText("Проверить связанную VL-SCH-BR-EPK схему"))
    }

    @Test
    fun rawMachineValuesDoNotLeakIntoCatalog() {
        assertEquals(
            "Действовать по местной инструкции и установленному технологическому процессу",
            technicalPresentationLine("LOCAL_PROCEDURE")
        )
        assertEquals(
            "Применимость определяется по фактическому исполнению секции",
            technicalPresentationLine("section-aware applicability")
        )
        assertEquals("Групповое регулирование", technicalPresentationLine("group_base"))
        assertEquals("3ЭС5К №896 и позднее", technicalPresentationLine("3es5k_896plus"))
        assertEquals("п. 5", technicalPresentationLine("p.5"))
        assertNull(technicalPresentationLine("confirmed_general"))
        assertNull(technicalPresentationLine("motorCompressor"))
        assertNull(technicalPresentationLine("gv"))
    }

    @Test
    fun ermakEnglishRulesAreLocalized() {
        assertEquals(
            "Сначала выбирается исполнение, затем строится подробная схема. Неизвестные параметры не подменяются другим исполнением автоматически.",
            technicalPresentationLine("Variant selection happens before detailed scheme rendering. Unknown dimensions do not silently default to another execution.")
        )
        assertEquals(
            "Бустерная секция применяется только для 3ЭС5К.",
            technicalPresentationLine("booster is only valid for 3ES5K")
        )
    }

    @Test
    fun publicStatusesStayReadable() {
        assertEquals("Остановиться и доложить", technicalStatusPresentation("STOP_AND_REPORT"))
        assertEquals("Требует сверки со схемой секции", technicalStatusPresentation("SECONDARY_SOURCE_REQUIRES_DRAWING"))
        assertNull(technicalStatusPresentation("SOME_NEW_INTERNAL_STATUS"))
    }
}
'''


def write_mirror(rel: str, content: str):
    for prefix in ("app", "patch/app"):
        path = ROOT / prefix / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")


def replace_once(path: Path, old: str, new: str):
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match in {path}: {old[:80]!r}; got {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


# 1. Pure presentation layer, mirrored source and regression tests.
write_mirror("src/main/java/ru/railbrake/calculator/core/TechnicalPresentation.kt", PRESENTATION)
write_mirror("src/main/java/ru/railbrake/calculator/ui/TechnicalCatalogScreen.kt", TECH_SCREEN)
write_mirror("src/test/java/ru/railbrake/calculator/core/TechnicalPresentationTest.kt", TESTS)

# 2. Make repository display/search paths use the sanitized presentation layer.
for prefix in ("app", "patch/app"):
    repo = ROOT / prefix / "src/main/java/ru/railbrake/calculator/core/TechnicalDataRepository.kt"
    replace_once(repo, ".let(::userFacingTechnicalText)\n                    ?.takeIf(String::isNotBlank)", ".let(::technicalPresentationLine)\n                    ?.takeIf(String::isNotBlank)")
    replace_once(repo, ".filterNot(::isInternalTechnicalReference)\n                    .map(::userFacingTechnicalText))", ".mapNotNull(::technicalPresentationLine))")

# 3. Restore one navigation language on the atlas landing screen: horizontal chips, not a different two-column button UI.
old_grid = '''        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            materials.chunked(2).forEach { rowItems ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems.forEach { (label, action) ->
                        OutlinedButton(onClick = action, modifier = Modifier.weight(1f)) { Text(label) }
                    }
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
'''
new_grid = '''        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(materials, key = { it.first }) { (label, action) ->
                FilterChip(
                    selected = false,
                    onClick = action,
                    label = { Text(label) }
                )
            }
        }
'''
for prefix in ("app", "patch/app"):
    app_file = ROOT / prefix / "src/main/java/ru/railbrake/calculator/ui/BrakeCalculatorApp.kt"
    replace_once(app_file, old_grid, new_grid)
    replace_once(app_file, '            "Локомотив / атлас",\n            "Выберите серию и тип материала"', '            "Локомотивы / атлас",\n            "Выберите серию и тип материала"')

# 4. Version the repaired build as dev17.
for prefix in ("app", "patch/app"):
    gradle = ROOT / prefix / "build.gradle.kts"
    replace_once(gradle, 'versionCode = 144', 'versionCode = 145')
    replace_once(gradle, 'versionName = "1.2.2-dev16"', 'versionName = "1.2.2-dev17"')

# 5. Make future CI verify the new presentation helper mirror.
build_workflow = ROOT / ".github/workflows/build-apk-from-zip.yml"
replace_once(
    build_workflow,
    '            "src/main/java/ru/railbrake/calculator/core/TechnicalDataRepository.kt"\n',
    '            "src/main/java/ru/railbrake/calculator/core/TechnicalDataRepository.kt"\n            "src/main/java/ru/railbrake/calculator/core/TechnicalPresentation.kt"\n'
)

# 6. Keep README release label aligned when the known dev16 text is present.
readme = ROOT / "README.md"
if readme.exists():
    text = readme.read_text(encoding="utf-8")
    text = text.replace("# Железнодорожный помощник 1.2.2-dev16", "# Железнодорожный помощник 1.2.2-dev17", 1)
    marker = "`1.2.2-dev16` сохраняет платформу dev13 и интеграцию технических пакетов ВЛ80С/Ермак, исправляет runtime-падение атласа из dev14 и закрывает обнаруженные на реальном устройстве дефекты представления каталога. Внутренние stable ID и машинные статусы больше не выводятся и не участвуют в пользовательском поиске; известные связи показываются названиями и остаются кликабельными, а рамки каталога используют выбранную палитру. CI отдельно проверяет наличие и валидность всех 14 технических JSON внутри debug- и signed release APK."
    replacement = "`1.2.2-dev17` исправляет регрессию presentation-слоя технической базы ВЛ80С/Ермак: машинные enum/slug/legacy-ID не выходят в пользовательский UI, правила исполнений локализованы, связанные материалы не обрезаются и не дублируются, а входной экран атласа использует ту же навигационную модель, что и каталог. Канонические технические JSON и stable ID не изменены."
    if marker in text:
        text = text.replace(marker, replacement, 1)
    readme.write_text(text, encoding="utf-8")

# 7. Hard assertions: mirrors must remain byte-identical for every changed mirrored file.
mirrored = [
    "build.gradle.kts",
    "src/main/java/ru/railbrake/calculator/core/TechnicalDataRepository.kt",
    "src/main/java/ru/railbrake/calculator/core/TechnicalPresentation.kt",
    "src/main/java/ru/railbrake/calculator/ui/TechnicalCatalogScreen.kt",
    "src/main/java/ru/railbrake/calculator/ui/BrakeCalculatorApp.kt",
    "src/test/java/ru/railbrake/calculator/core/TechnicalPresentationTest.kt",
]
for rel in mirrored:
    a = (ROOT / "app" / rel).read_bytes()
    b = (ROOT / "patch/app" / rel).read_bytes()
    if a != b:
        raise SystemExit(f"Mirror mismatch after repair: {rel}")

# 8. Assert the user-visible regression strings are covered by the presentation layer.
required_tokens = [
    "LOCAL_PROCEDURE",
    "section-aware applicability",
    "group_base",
    "axle_control_modern",
    "3es5k_434_experimental",
    "3es5k_896plus",
    "BLOK_2ES5K",
    "motorCompressor",
]
for token in required_tokens:
    if token not in PRESENTATION and token not in TESTS:
        raise SystemExit(f"Missing regression coverage for {token}")

print("dev17 UI regression repair applied successfully")
