from pathlib import Path
import re

ROOT = Path('.')

def read(path): return (ROOT / path).read_text(encoding='utf-8')
def write(path, text):
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding='utf-8')
def rep(text, old, new, label):
    if old not in text: raise SystemExit(f'missing anchor: {label}')
    return text.replace(old, new, 1)

for base in ['app', 'patch/app']:
    p=f'{base}/build.gradle.kts'; t=read(p)
    t=rep(t,'versionCode = 145','versionCode = 146',p+' code')
    t=rep(t,'versionName = "1.2.2-dev17"','versionName = "1.2.2-dev18"',p+' name')
    write(p,t)

    p=f'{base}/src/main/java/ru/railbrake/calculator/core/TechnicalDataRepository.kt'; t=read(p)
    t=rep(t,'''class TechnicalDataRepository(private val context: Context) {
    val entries: List<TechnicalEntry> by lazy {
        buildList {
            addAll(loadVl80sProfiles())
            addAll(loadVl80sEquipment())
            addAll(loadVl80sAcceptance())
            addAll(loadVl80sElectrical())
            addAll(loadVl80sPneumatic())
            addAll(loadVl80sDiagnostics())
            addAll(loadErmakProfiles())
            addAll(loadErmakSystems())
            addAll(loadErmakEquipment())
            addAll(loadErmakKnowledge())
            addAll(loadErmakDiagnostics())
            addAll(loadErmakSchemes())
        }
    }

    private val byId by lazy { entries.associateBy(TechnicalEntry::id) }
''','''class TechnicalDataRepository(private val context: Context) {
    companion object {
        private val sharedSectionCache = mutableMapOf<Pair<TechnicalFamily, TechnicalSection>, List<TechnicalEntry>>()
    }

    val entries: List<TechnicalEntry>
        get() = TechnicalFamily.entries.flatMap { family ->
            sections(family).flatMap { section -> sectionEntries(family, section) }
        }
''',p+' head')
    t=rep(t,'''    fun entries(family: TechnicalFamily, section: TechnicalSection, query: String = ""): List<TechnicalEntry> {
        val needle = query.trim().lowercase()
        return entries.filter { entry ->
            entry.family == family && entry.section == section &&
                (needle.isBlank() || needle in entry.searchText)
        }
    }

    fun entry(id: String): TechnicalEntry? = byId[id]

    fun displayLines(lines: List<String>): List<String> = lines.mapNotNull { line ->
        line.split(" • ")
            .mapNotNull { part ->
                part.takeUnless { entry(it) != null || isInternalTechnicalReference(it) }
                    ?.let(::technicalPresentationLine)
                    ?.takeIf(String::isNotBlank)
            }
            .distinct()
            .joinToString(" • ")
            .takeIf(String::isNotBlank)
    }

    fun referencedEntries(lines: List<String>): List<TechnicalEntry> =
        lines.flatMap { it.split(" • ") }.mapNotNull(::entry).distinctBy(TechnicalEntry::id)

    fun count(family: TechnicalFamily, section: TechnicalSection): Int =
        entries.count { it.family == family && it.section == section }

    private fun json(asset: String): JSONObject =
        context.assets.open(asset).bufferedReader().use { JSONObject(it.readText()) }
''','''    fun entries(family: TechnicalFamily, section: TechnicalSection, query: String = ""): List<TechnicalEntry> {
        val needle = query.trim().lowercase()
        return sectionEntries(family, section).filter { needle.isBlank() || needle in it.searchText }
    }

    fun entry(id: String): TechnicalEntry? {
        synchronized(sharedSectionCache) {
            sharedSectionCache.values.asSequence().flatten().firstOrNull { it.id == id }?.let { return it }
        }
        candidateSections(id).forEach { (family, section) ->
            sectionEntries(family, section).firstOrNull { it.id == id }?.let { return it }
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
        context.assets.open(asset).bufferedReader().use { JSONObject(it.readText()) }
''',p+' queries')
    pat=re.compile(r'''    private fun loadVl80sAcceptance\(\): List<TechnicalEntry> =\n        json\("technical/vl80s_acceptance\.json"\)\.array\("items"\)\.objects\(\)\.map \{ item ->.*?\n        \}\n\n    private fun loadVl80sElectrical''',re.S)
    m=pat.search(t)
    if not m: raise SystemExit('missing acceptance loader '+p)
    repl='''    private fun loadVl80sAcceptance(): List<TechnicalEntry> {
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

    private fun loadVl80sElectrical'''
    t=t[:m.start()]+repl+t[m.end():]
    write(p,t)

    p=f'{base}/src/main/java/ru/railbrake/calculator/core/TechnicalPresentation.kt'; t=read(p)
    t=rep(t,'    "local_procedure" to "Действовать по местной инструкции и установленному технологическому процессу",\n','''    "local_procedure" to "Действовать по местной инструкции и установленному технологическому процессу",
    "stop_and_report" to "Остановить проверку и доложить установленным порядком",
    "authorized_only" to "Только при установленном допуске и безопасной подготовке",
    "visual_safe" to "Только безопасный визуальный осмотр",
    "cab_only" to "Проверка выполняется из кабины в установленном порядке",
    "external_inspection_complete" to "Наружный осмотр завершён",
    "brake_area_clear" to "Зона тормозной передачи свободна от людей",
    "brakes_confirmed" to "Работоспособность тормозов подтверждена установленным порядком",
    "all_base_variants_verify_actual_section" to "Для всех базовых исполнений с обязательной проверкой фактической секции",
    "actual_section_scheme_required" to "Требуется схема фактической секции",
''',p+' labels')
    write(p,t)

    p=f'{base}/src/main/java/ru/railbrake/calculator/ui/TechnicalCatalogScreen.kt'; t=read(p)
    t=rep(t,'import androidx.compose.material3.FilterChip\n','import androidx.compose.material3.FilterChip\nimport androidx.compose.material3.FilterChipDefaults\n',p+' import')
    t=rep(t,'''fun TechnicalCatalogScreen(
    initialFamily: TechnicalFamily = TechnicalFamily.VL80S,
    initialSection: TechnicalSection = TechnicalSection.EQUIPMENT,
    sectionBackLabel: String = "Локомотивы / атлас",
    onSectionBack: () -> Unit
) {''','''fun TechnicalCatalogScreen(
    initialFamily: TechnicalFamily = TechnicalFamily.VL80S,
    initialSection: TechnicalSection = TechnicalSection.EQUIPMENT,
    sectionBackLabel: String = "Локомотивы / атлас",
    onSectionBack: () -> Unit,
    lockFamily: Boolean = false,
    lockSection: Boolean = false,
    initialEntryId: String? = null
) {''',p+' signature')
    t=rep(t,'    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }\n','    var selectedId by rememberSaveable(initialEntryId) { mutableStateOf(initialEntryId) }\n',p+' selected')
    t=rep(t,'    val availableSections = remember(family) { repository.sections(family) }\n','    val availableSections = remember(family, lockSection, initialSection) { if (lockSection) listOf(initialSection) else repository.sections(family) }\n',p+' sections')
    t=rep(t,'                items(listOf(TechnicalFamily.VL80S, TechnicalFamily.ERMAK)) { option ->\n','                items(if (lockFamily) listOf(family) else listOf(TechnicalFamily.VL80S, TechnicalFamily.ERMAK)) { option ->\n',p+' family list')
    t=rep(t,'''    val visible = remember(family, selectedSection, query) {
        repository.entries(family, selectedSection, query)
    }
''','''    val visible = remember(family, selectedSection, query) {
        val loaded = repository.entries(family, selectedSection, query)
        if (selectedSection == TechnicalSection.ACCEPTANCE && query.isBlank()) loaded.filter { it.status == "ROUTE" } else loaded
    }
''',p+' visible')
    t=rep(t,'"${option.title} · ${repository.count(family, option)}",\n','option.title,\n',p+' counts')
    t=rep(t,'''                    FilterChip(
                        selected = selectedSection == option,
                        onClick = { sectionName = option.name; query = "" },
                        label = {
                            Text(
                                option.title,
                                color = if (selectedSection == option) accent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )''','''                    FilterChip(
                        selected = selectedSection == option,
                        onClick = { sectionName = option.name; query = "" },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent.copy(alpha = 0.22f), selectedLabelColor = accent),
                        label = { Text(option.title, color = if (selectedSection == option) accent else MaterialTheme.colorScheme.onSurfaceVariant) }
                    )''',p+' chips')
    t=rep(t,'                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),\n','                colors = CardDefaults.cardColors(containerColor = technicalSectionContainer(entry.section)),\n',p+' card color')
    t=rep(t,'                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),\n                border = BorderStroke(1.dp, accent.copy(alpha = 0.38f)),\n','                colors = CardDefaults.cardColors(containerColor = technicalBlockContainer(entry.section, block.title)),\n                border = BorderStroke(1.dp, accent.copy(alpha = 0.38f)),\n',p+' block color')
    pat=re.compile(r'''@Composable\nprivate fun TechnicalSequence\(.*?\n}\n\n@Composable\nprivate fun technicalSectionAccent''',re.S); m=pat.search(t)
    if not m: raise SystemExit('missing sequence '+p)
    seq='''@Composable
private fun TechnicalSequence(entry: TechnicalEntry, repository: TechnicalDataRepository, onOpen: (TechnicalEntry) -> Unit) {
    var step by rememberSaveable(entry.id) { mutableIntStateOf(0) }
    var checkedIds by rememberSaveable(entry.id) { mutableStateOf("") }
    var noteIds by rememberSaveable("${entry.id}-notes") { mutableStateOf("") }
    val currentId=entry.sequence[step.coerceIn(entry.sequence.indices)]
    val target=repository.entry(currentId)
    val accent=technicalSectionAccent(entry.section,entry.status)
    val checked=checkedIds.split('|').filter(String::isNotBlank).toSet(); val noted=noteIds.split('|').filter(String::isNotBlank).toSet()
    Card(modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer),border=BorderStroke(1.dp,accent.copy(alpha=.45f)),shape=RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Пошаговая приёмка",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Black)
            Text("Шаг ${step+1} из ${entry.sequence.size}",color=accent)
            Text(target?.let(::technicalEntryTitle)?:"Пункт приёмки",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black)
            target?.let { item ->
                technicalEntrySubtitle(item)?.let { Text(it,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                item.blocks.forEach { block ->
                    val lines=repository.displayLines(block.lines).mapNotNull(::technicalPresentationLine).distinct()
                    if(lines.isNotEmpty()) Card(colors=CardDefaults.cardColors(containerColor=technicalBlockContainer(item.section,block.title)),modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) { Text(block.title,fontWeight=FontWeight.Black); lines.forEach { Text("• $it") } }
                    }
                }
                Text(when { currentId in noted->"Есть замечание"; currentId in checked->"Проверено"; else->"Не отмечено" },color=accent,fontWeight=FontWeight.Bold)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={ checkedIds=(checked+currentId).joinToString("|"); noteIds=(noted-currentId).joinToString("|") }) { Text("Проверено") }
                    OutlinedButton(onClick={ noteIds=(noted+currentId).joinToString("|"); checkedIds=(checked-currentId).joinToString("|") }) { Text("Замечание") }
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick={if(step>0)step--},enabled=step>0){Text("Назад")}; Button(onClick={if(step<entry.sequence.lastIndex)step++},enabled=step<entry.sequence.lastIndex){Text("Далее")} }
            if(target!=null) OutlinedButton(onClick={onOpen(target)},modifier=Modifier.fillMaxWidth()){Text("Открыть полную карточку")}
        }
    }
}

@Composable
private fun technicalBlockContainer(section: TechnicalSection,title:String):Color=when {
    title.contains("Опас",true)->MaterialTheme.colorScheme.errorContainer
    title.contains("Неисправ",true)->MaterialTheme.colorScheme.secondaryContainer
    title.contains("Нормаль",true)->MaterialTheme.colorScheme.primaryContainer
    title.contains("Провер",true)->MaterialTheme.colorScheme.tertiaryContainer
    else->technicalSectionContainer(section)
}

@Composable
private fun technicalSectionContainer(section:TechnicalSection):Color=when(section){
    TechnicalSection.PROFILES->MaterialTheme.colorScheme.tertiaryContainer
    TechnicalSection.SYSTEMS->MaterialTheme.colorScheme.secondaryContainer
    TechnicalSection.EQUIPMENT->MaterialTheme.colorScheme.primaryContainer
    TechnicalSection.KNOWLEDGE->MaterialTheme.colorScheme.tertiaryContainer
    TechnicalSection.DIAGNOSTICS->MaterialTheme.colorScheme.secondaryContainer
    TechnicalSection.ELECTRICAL->MaterialTheme.colorScheme.tertiaryContainer
    TechnicalSection.PNEUMATIC->MaterialTheme.colorScheme.primaryContainer
    TechnicalSection.ACCEPTANCE->MaterialTheme.colorScheme.secondaryContainer
}

@Composable
private fun technicalSectionAccent'''
    t=t[:m.start()]+seq+t[m.end():]
    write(p,t)

    p=f'{base}/src/main/java/ru/railbrake/calculator/ui/BrakeCalculatorApp.kt'; t=read(p)
    t=t.replace('SETTINGS("Настройки")','SETTINGS("Палитра")').replace('label = { Text("Настройки и палитра") }','label = { Text("Палитра") }')
    t=rep(t,'''                AppScreen.ACCEPTANCE -> key(acceptanceRootVersion) {
                    TechnicalCatalogScreen(
                        initialFamily = TechnicalFamily.VL80S,
                        initialSection = TechnicalSection.ACCEPTANCE,
                        sectionBackLabel = "Главная",
                        onSectionBack = { screenName = AppScreen.HOME.name }
                    )
                }
                AppScreen.DIAGNOSTICS -> key(diagnosticRootVersion) {
                    DiagnosticScreen(
                        initialScenarioId = diagnosticStartScenarioId,
                        initialEquipmentId = diagnosticStartEquipmentId
                    )
                }''','''                AppScreen.ACCEPTANCE -> key(acceptanceRootVersion) {
                    TechnicalCatalogScreen(
                        initialFamily = TechnicalFamily.VL80S,
                        initialSection = TechnicalSection.ACCEPTANCE,
                        sectionBackLabel = "Главная",
                        onSectionBack = { screenName = AppScreen.HOME.name },
                        lockFamily = true,
                        lockSection = true
                    )
                }
                AppScreen.DIAGNOSTICS -> key(diagnosticRootVersion) {
                    LocomotiveDiagnosticsScreen(
                        initialScenarioId = diagnosticStartScenarioId,
                        initialEquipmentId = diagnosticStartEquipmentId
                    )
                }''',p+' app screens')
    write(p,t)

    write(f'{base}/src/main/java/ru/railbrake/calculator/ui/LocomotiveDiagnosticsScreen.kt','''package ru.railbrake.calculator.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.railbrake.calculator.core.TechnicalFamily
import ru.railbrake.calculator.core.TechnicalSection

@Composable
fun LocomotiveDiagnosticsScreen(initialScenarioId:String?=null,initialEquipmentId:String?=null){
    val initialFamily=if(initialScenarioId?.startsWith("ER-DIAG-")==true||initialEquipmentId?.startsWith("ER-EQ-")==true) TechnicalFamily.ERMAK else TechnicalFamily.VL80S
    var familyName by rememberSaveable { mutableStateOf(initialFamily.name) }
    val family=runCatching{TechnicalFamily.valueOf(familyName)}.getOrDefault(TechnicalFamily.VL80S)
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            FilterChip(family==TechnicalFamily.VL80S,{familyName=TechnicalFamily.VL80S.name},label={Text("ВЛ80С")})
            FilterChip(family==TechnicalFamily.ERMAK,{familyName=TechnicalFamily.ERMAK.name},label={Text("Ермак")})
            Text("Алгоритмы разделены по серии",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=10.dp))
        }
        Box(Modifier.fillMaxWidth().weight(1f)){
            if(family==TechnicalFamily.VL80S) DiagnosticScreen(initialScenarioId?.takeUnless{it.startsWith("ER-")},initialEquipmentId?.takeUnless{it.startsWith("ER-")})
            else TechnicalCatalogScreen(
                initialFamily=TechnicalFamily.ERMAK,initialSection=TechnicalSection.DIAGNOSTICS,
                sectionBackLabel="Диагностика ВЛ80С",onSectionBack={familyName=TechnicalFamily.VL80S.name},
                lockFamily=true,lockSection=true,
                initialEntryId=initialScenarioId?.takeIf{it.startsWith("ER-DIAG-")}?:initialEquipmentId?.takeIf{it.startsWith("ER-EQ-")}
            )
        }
    }
}
''')

p='README.md'; t=read(p).replace('# Железнодорожный помощник 1.2.2-dev17','# Железнодорожный помощник 1.2.2-dev18')
if '`1.2.2-dev17`' in t: t=re.sub(r'`1\.2\.2-dev17`[^\n]*','`1.2.2-dev18` оптимизирует загрузку технической базы по разделам, возвращает маршруты пошаговой приёмки, разделяет диагностику ВЛ80С/Ермак и восстанавливает семантическую палитру технических карточек.',t,count=1)
write(p,t)

for rel in ['build.gradle.kts','src/main/java/ru/railbrake/calculator/core/TechnicalDataRepository.kt','src/main/java/ru/railbrake/calculator/core/TechnicalPresentation.kt','src/main/java/ru/railbrake/calculator/ui/TechnicalCatalogScreen.kt','src/main/java/ru/railbrake/calculator/ui/BrakeCalculatorApp.kt','src/main/java/ru/railbrake/calculator/ui/LocomotiveDiagnosticsScreen.kt']:
    if read('app/'+rel)!=read('patch/app/'+rel): raise SystemExit('mirror mismatch '+rel)
print('dev18 repair applied')
