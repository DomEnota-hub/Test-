package ru.railbrake.calculator.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.railbrake.calculator.core.Appendix12Input
import ru.railbrake.calculator.core.Appendix12Result
import ru.railbrake.calculator.core.AppendixFormula
import ru.railbrake.calculator.core.BrakeCalculator
import ru.railbrake.calculator.core.ConsistItem
import ru.railbrake.calculator.core.LocomotiveDatabase
import ru.railbrake.calculator.core.LocomotiveSpec
import ru.railbrake.calculator.core.MassCalculationInput
import ru.railbrake.calculator.core.MassCalculationResult
import ru.railbrake.calculator.core.MassSupplementResult
import ru.railbrake.calculator.core.ProfileMode
import ru.railbrake.calculator.core.TenTonsChoice
import ru.railbrake.calculator.data.HistoryRecord
import ru.railbrake.calculator.data.HistoryRepository
import ru.railbrake.calculator.ui.theme.AccentPalette
import ru.railbrake.calculator.ui.theme.Success
import ru.railbrake.calculator.ui.theme.Warning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private enum class AppScreen(val title: String) {
    MASS("По массе"),
    APPENDIX("ИДП №12"),
    LOCOMOTIVES("Локомотивы"),
    DIAGNOSTICS("Диагностика"),
    KNOWLEDGE("Справочник"),
    HISTORY("История"),
    COLORS("Цвета")
}

private enum class OutputMode(val title: String) {
    QUICK("Быстрый"),
    STUDY("Учебный")
}

private enum class MassSource(val title: String, val subtitle: String) {
    DIRECT("Масса + оси", "Готовые масса и количество осей"),
    WAGONS("По вагонам", "Оси считаются из 4-, 6- и 8-осных вагонов"),
    CONSIST("Сплотка", "Масса и оси из базы локомотивов"),
    MANUAL_LOAD("Нагрузка вручную", "Масса + известная фактическая нагрузка на ось")
}

private data class AppendixPrefill(
    val axleCount: Int?,
    val oilyRails: Boolean,
    val windSpeedMs: String,
    val windMatches: Boolean,
    val token: Long = System.nanoTime()
)

internal fun isDeveloperEasterEgg(massTons: Double, axleCount: Int?): Boolean =
    abs(massTons - 2381.0) < 1e-9 && axleCount == 999

@Composable
fun BrakeCalculatorApp(
    palette: AccentPalette,
    onPaletteChange: (AccentPalette) -> Unit
) {
    val context = LocalContext.current
    val historyRepository = remember { HistoryRepository(context) }
    var historyVersion by remember { mutableIntStateOf(0) }
    var screenName by rememberSaveable { mutableStateOf(AppScreen.MASS.name) }
    var appendixPrefill by remember { mutableStateOf<AppendixPrefill?>(null) }
    val screen = AppScreen.valueOf(screenName)
    val drawerState = androidx.compose.material3.rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier.padding(start = 24.dp, end = 20.dp, top = 22.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text("Железнодорожный помощник", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text("Разделы приложения", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AppScreen.entries.filter { it != AppScreen.COLORS }.forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(item.title) },
                        selected = screen == item,
                        onClick = {
                            screenName = item.name
                            drawerScope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    label = { Text(AppScreen.COLORS.title) },
                    selected = screen == AppScreen.COLORS,
                    onClick = {
                        screenName = AppScreen.COLORS.name
                        drawerScope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                AppHeader(onOpenMenu = { drawerScope.launch { drawerState.open() } })
                when (screen) {
                AppScreen.MASS -> ScrollPage {
                    MassScreen(
                        onHistory = {
                            historyRepository.add(it)
                            historyVersion++
                        },
                        onOpenAppendix = { prefill ->
                            appendixPrefill = prefill
                            screenName = AppScreen.APPENDIX.name
                        }
                    )
                }
                AppScreen.APPENDIX -> ScrollPage {
                    AppendixScreen(
                        prefill = appendixPrefill,
                        onHistory = {
                            historyRepository.add(it)
                            historyVersion++
                        }
                    )
                }
                AppScreen.HISTORY -> HistoryScreen(
                    repository = historyRepository,
                    version = historyVersion,
                    onCleared = { historyVersion++ }
                )
                AppScreen.LOCOMOTIVES -> LocomotiveReferenceScreen()
                AppScreen.DIAGNOSTICS -> DiagnosticScreen()
                AppScreen.KNOWLEDGE -> KnowledgeBaseScreen()
                AppScreen.COLORS -> ScrollPage {
                    PaletteScreen(palette, onPaletteChange)
                }
                }
            }
        }
    }
}

@Composable
private fun AppHeader(onOpenMenu: () -> Unit) {
    Row(
        modifier = Modifier.padding(start = 8.dp, end = 18.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onOpenMenu) {
            Text("☰", style = MaterialTheme.typography.headlineMedium)
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "Железнодорожный помощник",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
            Text(
                "Расчёты, локомотивы и интерактивный справочник",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScrollPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun MassScreen(
    onHistory: (HistoryRecord) -> Unit,
    onOpenAppendix: (AppendixPrefill) -> Unit
) {
    var outputModeName by rememberSaveable { mutableStateOf(OutputMode.QUICK.name) }
    var sourceName by rememberSaveable { mutableStateOf(MassSource.DIRECT.name) }
    val outputMode = OutputMode.valueOf(outputModeName)
    val source = MassSource.valueOf(sourceName)

    var mass by rememberSaveable { mutableStateOf("4000") }
    var directAxles by rememberSaveable { mutableStateOf("212") }
    var wag4 by rememberSaveable { mutableStateOf("53") }
    var wag6 by rememberSaveable { mutableStateOf("0") }
    var wag8 by rememberSaveable { mutableStateOf("0") }
    var extraAxles by rememberSaveable { mutableStateOf("0") }
    var manualLoad by rememberSaveable { mutableStateOf("18.87") }
    var slope by rememberSaveable { mutableStateOf(12.0) }
    var availableShoes by rememberSaveable { mutableStateOf("") }
    var axlesPerHandBrake by rememberSaveable { mutableStateOf("") }
    var tenChoiceName by rememberSaveable { mutableStateOf("") }
    var oily by rememberSaveable { mutableStateOf(false) }
    var wind by rememberSaveable { mutableStateOf("0") }
    var windMatches by rememberSaveable { mutableStateOf(false) }

    var consist by remember { mutableStateOf<List<ConsistItem>>(emptyList()) }
    var customName by rememberSaveable { mutableStateOf("") }
    var customMass by rememberSaveable { mutableStateOf("") }
    var customAxles by rememberSaveable { mutableStateOf("") }

    var result by remember { mutableStateOf<MassCalculationResult?>(null) }
    var supplement by remember { mutableStateOf<MassSupplementResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDeveloperEasterEgg by rememberSaveable { mutableStateOf(false) }

    fun invalidate() {
        result = null
        supplement = null
        error = null
        showDeveloperEasterEgg = false
    }

    SectionCard("Режим вывода", "В учебном режиме показываются формулы и логика выбора коэффициентов") {
        ChoiceRow(
            options = OutputMode.entries.map { it.title },
            selectedIndex = OutputMode.entries.indexOf(outputMode),
                onSelect = {
                    outputModeName = OutputMode.entries[it].name
                    if (OutputMode.entries[it] == OutputMode.QUICK) tenChoiceName = ""
                    invalidate()
            }
        )
    }

    WarningBox(
        "Таблица III.4 применяется для удержания грузового, грузопассажирского, почтово-багажного, рефрижераторного или хозяйственного поезда после остановки на перегоне, если автотормоза неисправны либо их невозможно привести в действие. Для станционного закрепления используйте расчёт по приложению №12 ИДП."
    )

    SectionCard("Исходные данные", "Выберите удобный способ задать состав") {
        MassSource.entries.forEach { option ->
            ChoiceOption(
                title = option.title,
                subtitle = option.subtitle,
                selected = source == option,
                onClick = {
                    sourceName = option.name
                    tenChoiceName = ""
                    invalidate()
                }
            )
        }

        Spacer(Modifier.height(2.dp))

        when (source) {
            MassSource.DIRECT -> {
                NumericField("Масса учитываемого состава, т", mass, true) { mass = it; invalidate() }
                NumericField("Количество учитываемых осей", directAxles, false) { directAxles = it; invalidate() }
            }
            MassSource.WAGONS -> {
                NumericField("Масса учитываемого состава, т", mass, true) { mass = it; invalidate() }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { NumericField("4-осных", wag4, false) { wag4 = it; invalidate() } }
                    Box(Modifier.weight(1f)) { NumericField("6-осных", wag6, false) { wag6 = it; invalidate() } }
                    Box(Modifier.weight(1f)) { NumericField("8-осных", wag8, false) { wag8 = it; invalidate() } }
                }
                NumericField("Дополнительных осей от прочих вагонов", extraAxles, false) { extraAxles = it; invalidate() }
                val counted = runCatching {
                    BrakeCalculator.wagonAxles(
                        wag4.toIntOrNull() ?: 0,
                        wag6.toIntOrNull() ?: 0,
                        wag8.toIntOrNull() ?: 0,
                        extraAxles.toIntOrNull() ?: 0
                    )
                }.getOrDefault(0)
                MiniMetric("Рассчитано осей", counted.toString())
            }
            MassSource.CONSIST -> {
                WarningBox("Расчёт локомотивной сплотки по таблице III.4 является только справочной оценкой: отдельное нормативное основание для применения этой таблицы к сплотке не подтверждено. Для закрепления сплотки используйте приложение №12 и фактическое число осей.")
                Text(
                    "Добавляйте только локомотивы, которые входят в расчёт. Ведущий локомотив не добавляйте, если по условию он не учитывается.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                WarningBox("Масса и число осей в справочнике могут отличаться по модификации. При известных точных данных используйте ручной ввод массы и осей.")
                LocomotiveConsistPicker(
                    consist = consist,
                    onChange = { consist = it; invalidate() }
                )
                val totalMass = consist.sumOf { it.totalMassTons }
                val totalAxles = consist.sumOf { it.totalAxles }
                if (consist.isNotEmpty()) {
                    MiniMetric("Сплотка", "${fmt(totalMass)} т • $totalAxles осей")
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Добавить нестандартный локомотив", fontWeight = FontWeight.SemiBold)
                NumericField("Название (можно оставить пустым)", customName, true, allowText = true) { customName = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { NumericField("Масса, т", customMass, true) { customMass = it } }
                    Box(Modifier.weight(1f)) { NumericField("Осей", customAxles, false) { customAxles = it } }
                }
                OutlinedButton(
                    onClick = {
                        val m = customMass.toRuDoubleOrNull()
                        val a = customAxles.toIntOrNull()
                        if (m != null && m > 0 && a != null && a > 0) {
                            val spec = LocomotiveSpec(
                                name = customName.ifBlank { "Другой локомотив" },
                                category = "Введено вручную",
                                massTons = m,
                                axles = a,
                                note = "Пользовательские данные"
                            )
                            consist = consist + ConsistItem(spec, 1)
                            customName = ""
                            customMass = ""
                            customAxles = ""
                            invalidate()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("Добавить в сплотку") }
            }
            MassSource.MANUAL_LOAD -> {
                NumericField("Масса учитываемого состава, т", mass, true) { mass = it; invalidate() }
                NumericField("Фактическая нагрузка на ось, т/ось", manualLoad, true) { manualLoad = it; invalidate() }
            }
        }
    }

    val previewLoad = when (source) {
        MassSource.DIRECT -> ratioOrNull(mass.toRuDoubleOrNull(), directAxles.toIntOrNull())
        MassSource.WAGONS -> {
            val ax = BrakeCalculator.wagonAxles(
                wag4.toIntOrNull() ?: 0,
                wag6.toIntOrNull() ?: 0,
                wag8.toIntOrNull() ?: 0,
                extraAxles.toIntOrNull() ?: 0
            )
            ratioOrNull(mass.toRuDoubleOrNull(), ax)
        }
        MassSource.CONSIST -> {
            val m = consist.sumOf { it.totalMassTons }
            val a = consist.sumOf { it.totalAxles }
            ratioOrNull(m, a)
        }
        MassSource.MANUAL_LOAD -> manualLoad.toRuDoubleOrNull()
    }

    if (previewLoad != null) {
        SectionCard("Нагрузка на ось", null) {
            MiniMetric(
                if (source == MassSource.MANUAL_LOAD) "Введённая нагрузка на ось" else "Средняя расчётная нагрузка",
                            "${fmt(previewLoad)} т/ось"
            )
            if (source != MassSource.MANUAL_LOAD) {
                Text(
                    "M/n — среднее расчётное значение. Для неоднородного состава при известных точных данных используйте ручной ввод нагрузки на ось.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when {
                previewLoad == 10.0 -> {
                    StatusText("Нормативно применяется строка: 10 тс/ось и более", true)
                    if (outputMode == OutputMode.STUDY) {
                        Text(
                            "Нижняя строка доступна только для сравнения, это не нормативный вариант при 10,00 т/ось.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ChoiceRow(
                            options = listOf("Нормативно: ≥ 10", "< 10 — только сравнение"),
                            selectedIndex = if (tenChoiceName == TenTonsChoice.LESS_THAN_10.name) 1 else 0,
                            onSelect = {
                                tenChoiceName = if (it == 1) TenTonsChoice.LESS_THAN_10.name else TenTonsChoice.TEN_OR_MORE.name
                                invalidate()
                            }
                        )
                    }
                }
                previewLoad < 10.0 -> StatusText("Будет применена строка: менее 10 тс/ось", true)
                else -> StatusText("Будет применена строка: 10 тс/ось и более", true)
            }
        }
    }

    SectionCard("Уклон", "Дискретные значения таблицы расчёта по массе") {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(BrakeCalculator.massSlopesPermille) { value ->
                FilterChip(
                    selected = value == slope,
                    onClick = { slope = value; invalidate() },
                    label = { Text("${fmt(value)}‰") }
                )
            }
        }
    }

    SectionCard("Фактические средства", "Башмаки используются первыми, ручной тормоз дополняет только недостающую часть") {
        NumericField("Доступно тормозных башмаков (например, 16)", availableShoes, false) { availableShoes = it; invalidate() }
        NumericField("Оси на одну единицу ручного тормоза (например, 4)", axlesPerHandBrake, false) { axlesPerHandBrake = it; invalidate() }
    }

    error?.let { ErrorBox(it) }

    CalculateButton {
        runCatching {
            val massValue: Double
            val axleCount: Int?
            val manualAxleLoad: Double?

            when (source) {
                MassSource.DIRECT -> {
                    massValue = mass.requireRuDouble("Введите массу")
                    axleCount = directAxles.requirePositiveInt("Введите количество осей")
                    manualAxleLoad = null
                }
                MassSource.WAGONS -> {
                    massValue = mass.requireRuDouble("Введите массу")
                    val counted = BrakeCalculator.wagonAxles(
                        wag4.toIntOrNull() ?: 0,
                        wag6.toIntOrNull() ?: 0,
                        wag8.toIntOrNull() ?: 0,
                        extraAxles.toIntOrNull() ?: 0
                    )
                    require(counted > 0) { "Количество осей по вагонам получилось 0" }
                    axleCount = counted
                    manualAxleLoad = null
                }
                MassSource.CONSIST -> {
                    require(consist.isNotEmpty()) { "Добавьте хотя бы один локомотив в сплотку" }
                    massValue = consist.sumOf { it.totalMassTons }
                    axleCount = consist.sumOf { it.totalAxles }
                    manualAxleLoad = null
                }
                MassSource.MANUAL_LOAD -> {
                    massValue = mass.requireRuDouble("Введите массу")
                    axleCount = null
                    manualAxleLoad = manualLoad.requireRuDouble("Введите нагрузку на ось")
                }
            }

            val tenChoice = tenChoiceName
                .takeIf { outputMode == OutputMode.STUDY && it.isNotBlank() }
                ?.let(TenTonsChoice::valueOf)
            val massResult = BrakeCalculator.calculateMass(
                MassCalculationInput(
                    massTons = massValue,
                    axleCount = axleCount,
                    manualAxleLoadTons = manualAxleLoad,
                    slopePermille = slope,
                    tenTonsChoice = tenChoice
                )
            )
            val supplementResult = BrakeCalculator.calculateMassSupplement(
                massResult,
                availableShoes.requireNonNegativeInt("Введите количество доступных башмаков"),
                axlesPerHandBrake.requirePositiveInt("Введите число осей на одну единицу ручного тормоза")
            )
            result = massResult
            supplement = supplementResult
            error = null
            showDeveloperEasterEgg = source == MassSource.DIRECT &&
                isDeveloperEasterEgg(massValue, axleCount)

            onHistory(
                HistoryRecord(
                    timestampMillis = System.currentTimeMillis(),
                    mode = "По массе",
                    title = "${fmt(massResult.massTons)} т • ${fmt(massResult.slopePermille)}‰",
                    summary = "${massResult.requiredShoes} ТБ • ${massResult.fullManualBrakeAxles} ручн. осей",
                    details = if (supplementResult.shoeShortage > 0) {
                        "Доступно ${supplementResult.availableShoes} ТБ, не хватает ${supplementResult.shoeShortage}; дополнение ${supplementResult.additionalManualAxles} ручн. осей"
                    } else {
                        "Башмаков достаточно, запас ${supplementResult.availableShoes - massResult.requiredShoes}"
                    }
                )
            )
        }.onFailure { error = it.message ?: "Не удалось выполнить расчёт" }
    }

    if (result != null && supplement != null) {
        MassResultCard(result!!, supplement!!, outputMode == OutputMode.STUDY)

        SectionCard(
            "Условия станционного закрепления",
            "Дополнительная проверка по Приложению №12 ИДП"
        ) {
            SwitchRow(
                "Замасленные рельсы",
                "В расчёте ИДП норма увеличивается в 1,5 раза",
                oily
            ) { oily = it }
            NumericField("Скорость ветра, м/с", wind, true) {
                wind = it
                if ((it.toRuDoubleOrNull() ?: 0.0) <= 15.0) windMatches = false
            }
            if ((wind.toRuDoubleOrNull() ?: 0.0) > 15.0) {
                SwitchRow(
                    "Ветер в опасном направлении",
                    "Совпадает с направлением возможного самопроизвольного движения",
                    windMatches
                ) { windMatches = it }
            }
            WarningBox(
                "Замасленность и ветер не изменяют расчёт по массе. " +
                    "Они учитываются отдельным расчётом по Приложению №12 ИДП."
            )
            Button(
                onClick = {
                    val windValue = wind.toRuDoubleOrNull()
                    if (windValue == null || !windValue.isFinite() || windValue < 0.0) {
                        error = "Введите конечную неотрицательную скорость ветра"
                    } else if (result!!.axleCount == null) {
                        error = "Для перехода к ИДП укажите количество осей: нагрузка введена вручную"
                    } else {
                        onOpenAppendix(
                            AppendixPrefill(
                                axleCount = result!!.axleCount,
                                oilyRails = oily,
                                windSpeedMs = fmt(windValue),
                                windMatches = windMatches
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Рассчитать закрепление по ИДП №12") }
        }
    }

    if (showDeveloperEasterEgg) {
        AlertDialog(
            onDismissRequest = { showDeveloperEasterEgg = false },
            title = { Text("Разработчик") },
            text = { Text("Кузнецов Д.С. (DomEnota)") },
            confirmButton = {
                TextButton(onClick = { showDeveloperEasterEgg = false }) {
                    Text("Хорошо")
                }
            }
        )
    }

    SafetyNotice()
}

@Composable
private fun MassResultCard(
    result: MassCalculationResult,
    supplement: MassSupplementResult,
    study: Boolean
) {
    val enough = supplement.shoeShortage == 0
    HeroResult(
        title = "${result.requiredShoes} башмак(ов)",
        subtitle = if (enough) "Доступного количества достаточно" else "Не хватает ${supplement.shoeShortage}, потребуется дополнение ручным тормозом",
        success = enough
    )

    SectionCard("Расчёт", null) {
        Metric("Масса", "${fmt(result.massTons)} т")
        result.axleCount?.let { Metric("Оси", it.toString()) }
        Metric(
            if (result.axleCount != null) "Средняя расчётная нагрузка" else "Введённая нагрузка на ось",
            "${fmt(result.axleLoadTons)} т/ось"
        )
        Metric("Категория III.4", if (result.heavyCategory) "10 тс/ось и более" else "менее 10 тс/ось")
        Metric("Уклон", "${fmt(result.slopePermille)}‰")
        Metric("Требуется башмаков", result.requiredShoes.toString(), true)
        Metric("Полная норма ручных тормозных осей", result.fullManualBrakeAxles.toString(), true)
        Metric("Доступно башмаков", supplement.availableShoes.toString())
        if (result.isExactlyTenTons && !result.heavyCategory) {
            WarningBox("Показано учебное сравнение по строке <10 т/ось. При ровно 10,00 т/ось нормативно применяется строка «10 т/ось и более».")
        }
    }

    if (!enough) {
        SectionCard("Дополнение ручным тормозом", "Имеющиеся башмаки продолжают использоваться") {
            Metric("Недостаёт башмаков", supplement.shoeShortage.toString(), true)
            Metric("Дополнительно ручных тормозных осей", supplement.additionalManualAxles.toString(), true)
            Metric("Единиц с ручным тормозом", supplement.requiredHandBrakeUnits.toString(), true)
            Metric("Фактически заторможено осей", supplement.actuallyBrakedAxles.toString())
            Metric("Запас по осям", supplement.reserveManualAxles.toString())
            WarningBox("Пропорциональное дополнение башмаков ручными тормозами является расчётной функцией приложения. Для эксплуатационного применения сверяйте установленный порядок.")
        }
    }

    if (!study) {
        Text(
            "Дробные результаты округляются вверх как реализация приложения; это не выдаётся за отдельную дословную строку таблицы III.4.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (study) {
        SectionCard("Как рассчитано", "Учебный разбор") {
            result.axleCount?.let {
                Formula("Средняя расчётная нагрузка: q = M / n = ${fmt(result.massTons)} / $it = ${fmt(result.axleLoadTons)} т/ось")
            }
            Formula("K = M × k / 100 = ${fmt(result.massTons)} × ${fmt(result.shoeCoefficientPer100Tons)} / 100 = ${fmt(result.shoesExact)} → ${result.requiredShoes} ТБ")
            Formula("Nруч = M × kруч / 100 = ${fmt(result.massTons)} × ${fmt(result.manualAxleCoefficientPer100Tons)} / 100 = ${fmt(result.manualAxlesExact)} → ${result.fullManualBrakeAxles} осей")
            Text(
                "Дробные результаты округляются вверх как реализация приложения; это пояснение не выдаётся за отдельную дословную норму таблицы III.4.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (supplement.shoeShortage > 0) {
                Formula("осей/ТБ = ${fmt(result.manualAxleCoefficientPer100Tons)} / ${fmt(result.shoeCoefficientPer100Tons)} = ${fmt(supplement.manualAxlesPerShoeEquivalent)}")
                Formula("Остаток = max(0; ${fmt(result.shoesExact)} − ${supplement.availableShoes}) = ${fmt(supplement.remainingShoeEquivalent)} ТБ")
                Formula("Nдоп = ${fmt(supplement.remainingShoeEquivalent)} × ${fmt(supplement.manualAxlesPerShoeEquivalent)} = ${fmt(supplement.additionalManualAxlesExact)} → ${supplement.additionalManualAxles} осей")
            }
        }
    }
}

@Composable
private fun LocomotiveConsistPicker(
    consist: List<ConsistItem>,
    onChange: (List<ConsistItem>) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Поиск локомотива") },
        singleLine = true,
        shape = RoundedCornerShape(16.dp)
    )

    val found = remember(query) { LocomotiveDatabase.search(query).take(8) }
    found.forEach { loco ->
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            onClick = {
                val existing = consist.indexOfFirst { it.locomotive.name == loco.name }
                val updated = if (existing >= 0) {
                    consist.toMutableList().also { list ->
                        val old = list[existing]
                        list[existing] = old.copy(quantity = old.quantity + 1)
                    }
                } else consist + ConsistItem(loco, 1)
                onChange(updated)
            }
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(loco.name, fontWeight = FontWeight.Bold)
                Text(
                    "${fmt(loco.massTons)} т • ${loco.axles} осей • ${loco.category}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (consist.isNotEmpty()) {
        Text("В сплотке", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        consist.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${item.locomotive.name} × ${item.quantity}", fontWeight = FontWeight.SemiBold)
                    Text(
                        "${fmt(item.totalMassTons)} т • ${item.totalAxles} осей",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = {
                        val list = consist.toMutableList()
                        if (item.quantity <= 1) list.removeAt(index)
                        else list[index] = item.copy(quantity = item.quantity - 1)
                        onChange(list)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("−") }
                OutlinedButton(
                    onClick = {
                        val list = consist.toMutableList()
                        list[index] = item.copy(quantity = item.quantity + 1)
                        onChange(list)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("+") }
            }
        }
    }
}

@Composable
private fun AppendixScreen(
    prefill: AppendixPrefill?,
    onHistory: (HistoryRecord) -> Unit
) {
    var axles by rememberSaveable(prefill?.token) { mutableStateOf(prefill?.axleCount?.toString() ?: "") }
    var profileName by rememberSaveable { mutableStateOf(ProfileMode.NORMAL.name) }
    var slope by rememberSaveable { mutableStateOf("8") }
    var formulaName by rememberSaveable { mutableStateOf(AppendixFormula.FORMULA_1.name) }
    var oily by rememberSaveable(prefill?.token) { mutableStateOf(prefill?.oilyRails ?: false) }
    var wind by rememberSaveable(prefill?.token) { mutableStateOf(prefill?.windSpeedMs ?: "0") }
    var windMatches by rememberSaveable(prefill?.token) { mutableStateOf(prefill?.windMatches ?: false) }
    var availableShoes by rememberSaveable { mutableStateOf("") }
    var axlesPerHandBrake by rememberSaveable { mutableStateOf("") }
    var leavingWithoutLocomotive by rememberSaveable { mutableStateOf(true) }
    var point20ConditionName by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<Appendix12Result?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val profile = ProfileMode.valueOf(profileName)
    val slopeValue = slope.toRuDoubleOrNull() ?: 0.0

    SectionCard("Закрепляемая группа", "Расчёт по конкретному пути или его отрезку") {
        NumericField("Количество осей", axles, false) { axles = it; result = null }
        Text("Подсказка по выбору уклона", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChoiceOption("Обычный", "Уклон задан для пути", profile == ProfileMode.NORMAL) { profileName = ProfileMode.NORMAL.name; result = null }
        ChoiceOption("Ломаный, весь путь", "Используется средний уклон всего пути", profile == ProfileMode.BROKEN_FULL_TRACK) { profileName = ProfileMode.BROKEN_FULL_TRACK.name; result = null }
        ChoiceOption("Отдельный отрезок", "Используется фактический уклон отрезка", profile == ProfileMode.SEPARATE_SEGMENT) { profileName = ProfileMode.SEPARATE_SEGMENT.name; result = null }
        NumericField("Уклон, ‰", slope, true) { slope = it; result = null }
    }

    if (slopeValue > 0.5) {
        SectionCard("Расчётная формула", null) {
            ChoiceOption(
                "Формула №1",
                "K = n × (1,5i + 1) / 200. Однородный состав; также смешанный, если башмаки укладываются под вагоны ≥15 т/ось, а при их отсутствии — под наиболее тяжёлые вагоны группы.",
                formulaName == AppendixFormula.FORMULA_1.name
            ) { formulaName = AppendixFormula.FORMULA_1.name; result = null }
            ChoiceOption(
                "Формула №2",
                "K = n × (4i + 1) / 200. Башмаки укладываются под порожние вагоны; под вагоны <15 т/ось, если они не самые тяжёлые в группе; либо нагрузка на ось неизвестна.",
                formulaName == AppendixFormula.FORMULA_2.name
            ) { formulaName = AppendixFormula.FORMULA_2.name; result = null }
        }
    }

    SectionCard("Условия", "Эти факторы относятся именно к станционному закреплению") {
        SwitchRow(
            "Состав оставляется без локомотива",
            "Проверка специальных условий п. 20 приложения №12 ИДП",
            leavingWithoutLocomotive
        ) { leavingWithoutLocomotive = it; result = null }
        if (leavingWithoutLocomotive && slopeValue > 2.5) {
            WarningBox("При уклоне более 2,5‰ оставление без локомотива запрещено, кроме случаев п. 20 приложения №12. Выберите реально выполненное условие; без него расчёт блокируется.")
            ChoiceOption(
                "Маршрут со стороны спуска защищён или изолирован",
                "Условие подтверждено ТРА и фактической обстановкой",
                point20ConditionName == "route"
            ) { point20ConditionName = "route"; result = null }
            ChoiceOption(
                "Применено стационарное устройство закрепления",
                "Параметры устройства достаточны для этой группы",
                point20ConditionName == "device"
            ) { point20ConditionName = "device"; result = null }
        }
        SwitchRow("Замасленные рельсы", "Норма по п. 8 увеличивается в 1,5 раза", oily) { oily = it; result = null }
        NumericField("Скорость ветра, м/с", wind, true) { wind = it; result = null }
        if ((wind.toRuDoubleOrNull() ?: 0.0) > 15.0) {
            SwitchRow(
                "Ветер в опасном направлении",
                "Совпадает с направлением возможного самопроизвольного движения",
                windMatches
            ) { windMatches = it; result = null }
        }
    }

    SectionCard("Фактические средства", null) {
        NumericField("Доступно тормозных башмаков (например, 16)", availableShoes, false) { availableShoes = it; result = null }
        NumericField("Оси на одну единицу стояночного тормоза (например, 4)", axlesPerHandBrake, false) { axlesPerHandBrake = it; result = null }
    }

    error?.let { ErrorBox(it) }

    CalculateButton {
        runCatching {
            val resultValue = BrakeCalculator.calculateAppendix12(
                Appendix12Input(
                    axleCount = axles.requirePositiveInt("Введите количество осей"),
                    profileMode = profile,
                    slopePermille = slope.requireNonNegativeDouble("Введите уклон"),
                    formula = if (slopeValue <= 0.5) null else AppendixFormula.valueOf(formulaName),
                    oilyRails = oily,
                    windSpeedMs = wind.requireNonNegativeDouble("Введите скорость ветра"),
                    windDirectionMatchesPossibleMovement = windMatches,
                    availableShoes = availableShoes.requireNonNegativeInt("Введите количество доступных башмаков"),
                    axlesPerHandBrakeUnit = axlesPerHandBrake.requirePositiveInt("Введите число осей на одну единицу стояночного тормоза"),
                    leavingWithoutLocomotive = leavingWithoutLocomotive,
                    point20ConditionConfirmed = point20ConditionName.isNotBlank()
                )
            )
            result = resultValue
            error = null
            onHistory(
                HistoryRecord(
                    timestampMillis = System.currentTimeMillis(),
                    mode = "ИДП №12",
                    title = "${resultValue.totalRequiredShoes} ТБ • ${fmt(slopeValue)}‰",
                    summary = "${axles.toIntOrNull() ?: 0} осей • доступно ${resultValue.availableShoes} ТБ",
                    details = if (resultValue.shoeShortage > 0) {
                        if (resultValue.specialUnderHalfPermilleRuleUsed) {
                            if (resultValue.extraShoesAfterSpecialRule > 0) {
                                "Уклон <0,5‰: 1 стояночный тормоз вместо базовой пары; дополнительная нехватка ${resultValue.extraShoesAfterSpecialRule} ТБ → ${resultValue.substituteBrakeAxles} тормозных осей"
                            } else {
                                "Уклон <0,5‰: вместо базовых башмаков с обеих сторон — 1 единица со стояночным тормозом"
                            }
                        } else {
                            "Не хватает ${resultValue.shoeShortage} ТБ; замена ${resultValue.substituteBrakeAxles} тормозных осей"
                        }
                    } else "Норма по количеству башмаков обеспечена"
                )
            )
        }.onFailure { error = it.message ?: "Не удалось выполнить расчёт" }
    }

    result?.let { AppendixResultCard(it, slopeValue, oily) }
    SafetyNotice()
}

@Composable
private fun AppendixResultCard(result: Appendix12Result, slope: Double, oily: Boolean) {
    val enough = result.shoeShortage == 0
    HeroResult(
        title = "${result.totalRequiredShoes} башмак(ов)",
        subtitle = if (enough) "Норма по количеству башмаков обеспечена" else "Не хватает ${result.shoeShortage} башмак(ов)",
        success = enough
    )
    SectionCard("Разбор закрепления", null) {
        Metric(
            if (result.isLowSlopeRule) "Базовая норма всего" else "Со стороны спуска",
            result.baseShoes.toString()
        )
        if (result.oppositeSideShoes > 0) Metric("С противоположной стороны", result.oppositeSideShoes.toString())
        if (result.windShoes > 0) Metric("Дополнительно из-за ветра", result.windShoes.toString())
        Metric("Итого требуется", result.totalRequiredShoes.toString(), true)
        Metric("Доступно", result.availableShoes.toString())
        if (oily) Metric("Замасленные рельсы", "×1,5")
        if (result.isLowSlopeRule) {
            Formula("Уклон ${fmt(slope)}‰ ≤ 0,5‰: базово по одному башмаку с каждой стороны")
            if (result.baseShoes > 2) {
                WarningBox("После увеличения нормы требуется ${result.baseShoes} башмака. Распределение дополнительных башмаков уточняется по ТРА/локальному нормативному акту.")
            }
        } else {
            val formula = when (result.formulaUsed) {
                AppendixFormula.FORMULA_1 -> "K = n × (1,5i + 1) / 200"
                AppendixFormula.FORMULA_2 -> "K = n × (4i + 1) / 200"
                null -> ""
            }
            Formula("$formula = ${fmt(result.baseExactBeforeOil)}${if (oily) " → ×1,5 = ${fmt(result.baseExactAfterOil)}" else ""} → ${result.baseShoes}")
        }
    }
    if (!enough) {
        SectionCard(
            "Недостаток башмаков",
            if (result.specialUnderHalfPermilleRuleUsed)
                "При уклоне <0,5‰ использована специальная норма стояночного тормоза"
            else
                "По Приложению №12: 5 тормозных осей заменяют 1 башмак в предусмотренных случаях"
        ) {
            Metric("Не хватает башмаков", result.shoeShortage.toString(), true)
            if (result.specialUnderHalfPermilleRuleUsed) {
                Metric("Специальная замена", "1 стояночный тормоз вместо базовых башмаков с обеих сторон", true)
                if (result.extraShoesAfterSpecialRule > 0) {
                    Metric("Доп. нехватка после спец. нормы", "${result.extraShoesAfterSpecialRule} ТБ")
                    Metric("Для доп. нехватки требуется осей", result.substituteBrakeAxles.toString(), true)
                }
            } else {
                Metric("Требуется тормозных осей", result.substituteBrakeAxles.toString(), true)
            }
            Metric("Единиц со стояночным тормозом", result.requiredHandBrakeUnits.toString(), true)
            Metric("Фактически задействовано осей", result.actuallyBrakedAxles.toString())
            if (!result.specialUnderHalfPermilleRuleUsed || result.extraShoesAfterSpecialRule > 0) {
                Metric("Запас по осям", result.reserveBrakeAxles.toString())
            }
            WarningBox("Замена башмаков стояночными тормозами применяется только в случаях, предусмотренных нормативным и локальным порядком.")
        }
    }
}

@Composable
private fun HistoryScreen(repository: HistoryRepository, version: Int, onCleared: () -> Unit) {
    val records = remember(version) { repository.load() }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Последние расчёты", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Хранятся локально на телефоне", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (records.isNotEmpty()) {
                OutlinedButton(onClick = { repository.clear(); onCleared() }, shape = RoundedCornerShape(14.dp)) { Text("Очистить") }
            }
        }

        if (records.isEmpty()) {
            EmptyCard("История пока пуста. После первого расчёта здесь появится запись.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(records) { record ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row {
                                Text(record.mode, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                                Text(formatDate(record.timestampMillis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(record.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(record.summary, style = MaterialTheme.typography.bodyMedium)
                            Text(record.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocomotiveReferenceScreen() {
    var query by rememberSaveable { mutableStateOf("") }
    val found = remember(query) { LocomotiveDatabase.search(query) }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("База локомотивов", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Масса и число осей справочные и могут отличаться по модификации. Если известны точные данные конкретной машины, используйте ручной ввод — он имеет приоритет над справочником.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Поиск по серии или типу") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(found) { loco -> LocomotiveCard(loco) }
        }
    }
}

@Composable
private fun LocomotiveCard(loco: LocomotiveSpec) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(loco.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${fmt(loco.massTons)} т", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("  •  ${loco.axles} ос.", fontWeight = FontWeight.SemiBold)
            }
            Text(loco.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (loco.note.isNotBlank()) Text(loco.note, style = MaterialTheme.typography.labelSmall, color = Warning)
            Text(loco.massKind, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(loco.sourceNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PaletteScreen(
    palette: AccentPalette,
    onPaletteChange: (AccentPalette) -> Unit
) {
    SectionCard("Цветовая палитра", "Выбранный акцент сохраняется после перезапуска приложения") {
        AccentPalette.entries.forEach { option ->
            ChoiceOption(
                title = option.title,
                subtitle = when (option) {
                    AccentPalette.BLUE -> "Спокойный холодный акцент"
                    AccentPalette.GREEN -> "Яркий зелёный акцент"
                    AccentPalette.YELLOW -> "Контрастный сигнальный акцент"
                    AccentPalette.PURPLE -> "Мягкий фиолетовый акцент"
                    AccentPalette.RED -> "Тёплый красный акцент"
                },
                selected = palette == option,
                onClick = { onPaletteChange(option) }
            )
        }
    }
    SafetyNotice()
}

@Composable
private fun SectionCard(title: String, subtitle: String?, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            content()
        }
    }
}

@Composable
private fun ChoiceOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChoiceRow(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, title ->
            if (selectedIndex == index) {
                Button(
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(15.dp)
                ) { Text(title, maxLines = 2) }
            } else {
                OutlinedButton(
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(15.dp)
                ) { Text(title, maxLines = 2) }
            }
        }
    }
}

@Composable
private fun NumericField(
    label: String,
    value: String,
    decimal: Boolean,
    allowText: Boolean = false,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next ->
            val filtered = when {
                allowText -> next
                decimal -> next.filter { it.isDigit() || it == ',' || it == '.' }
                else -> next.filter { it.isDigit() }
            }
            onValueChange(filtered)
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                allowText -> KeyboardType.Text
                decimal -> KeyboardType.Decimal
                else -> KeyboardType.Number
            }
        )
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CalculateButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Text("Рассчитать", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun HeroResult(title: String, subtitle: String, success: Boolean) {
    val color = if (success) Success else Warning
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.42f))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = color)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = color)
        }
    }
}

@Composable
private fun Metric(label: String, value: String, strong: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(0.7f), style = MaterialTheme.typography.bodyMedium, fontWeight = if (strong) FontWeight.Black else FontWeight.SemiBold)
    }
}

@Composable
private fun MiniMetric(label: String, value: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Formula(text: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)) {
        Text(text, modifier = Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatusText(text: String, success: Boolean) {
    Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = if (success) Success else Warning)
}

@Composable
private fun WarningBox(text: String) {
    Surface(shape = RoundedCornerShape(15.dp), color = Warning.copy(alpha = 0.08f), border = BorderStroke(1.dp, Warning.copy(alpha = 0.32f))) {
        Text(text, modifier = Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall, color = Warning)
    }
}

@Composable
private fun ErrorBox(text: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Проверьте данные", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Text(text, modifier = Modifier.fillMaxWidth().padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SafetyNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Учебный и проверочный инструмент", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                "Не заменяет ТРА станции, локальные нормативные акты, команды ДСП/ДНЦ и обязательный установленный порядок. Перед практическим применением сверяйте исходные данные и действующие нормы.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

private fun String.toRuDoubleOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()

private fun String.requireRuDouble(message: String): Double {
    val value = toRuDoubleOrNull()
    require(value != null && value > 0.0) { message }
    return value
}

private fun String.requireNonNegativeDouble(message: String): Double {
    val value = toRuDoubleOrNull()
    require(value != null && value >= 0.0) { message }
    return value
}

private fun String.requirePositiveInt(message: String): Int {
    val value = toIntOrNull()
    require(value != null && value > 0) { message }
    return value
}

private fun String.requireNonNegativeInt(message: String): Int {
    val value = toIntOrNull()
    require(value != null && value >= 0) { message }
    return value
}

private fun ratioOrNull(mass: Double?, axles: Int?): Double? {
    if (mass == null || mass <= 0.0 || axles == null || axles <= 0) return null
    return mass / axles
}

private fun fmt(value: Double): String = String.format(Locale.US, "%.2f", value)
    .trimEnd('0')
    .trimEnd('.')
    .replace('.', ',')

private fun formatDate(timestamp: Long): String = SimpleDateFormat("dd.MM HH:mm", Locale("ru", "RU")).format(Date(timestamp))
