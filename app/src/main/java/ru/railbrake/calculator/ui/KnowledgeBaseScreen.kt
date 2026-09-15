package ru.railbrake.calculator.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.railbrake.calculator.R
import ru.railbrake.calculator.core.AirRoute
import ru.railbrake.calculator.core.DiagramHotspot
import ru.railbrake.calculator.core.KnowledgeArticle
import ru.railbrake.calculator.core.KnowledgeRepository
import ru.railbrake.calculator.core.PneumaticScenario
import ru.railbrake.calculator.data.FavoriteArticleRepository

@Composable
fun KnowledgeBaseScreen() {
    var selectedArticleId by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val favoritesRepository = remember { FavoriteArticleRepository(context) }
    var favoriteIds by remember { mutableStateOf(favoritesRepository.load()) }
    val article = selectedArticleId?.let(KnowledgeRepository::articleById)

    fun toggleFavorite(articleId: String) {
        favoriteIds = favoritesRepository.toggle(articleId)
    }

    if (article == null) {
        KnowledgeHome(
            favoriteIds = favoriteIds,
            onOpenArticle = { selectedArticleId = it.id },
            onToggleFavorite = ::toggleFavorite
        )
    } else {
        KnowledgeArticleScreen(
            article = article,
            favoriteIds = favoriteIds,
            onBack = { selectedArticleId = null },
            onOpenArticle = { selectedArticleId = it.id },
            onToggleFavorite = ::toggleFavorite
        )
    }
}

@Composable
private fun KnowledgeHome(
    favoriteIds: Set<String>,
    onOpenArticle: (KnowledgeArticle) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Все") }
    var onlyFavorites by rememberSaveable { mutableStateOf(false) }
    val results = remember(query, category, onlyFavorites, favoriteIds) {
        KnowledgeRepository.search(query, category).filter { !onlyFavorites || it.id in favoriteIds }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Карманная железнодорожная база", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Материалы хранятся в приложении и доступны офлайн. Для открытия внешних ссылок на первоисточники требуется интернет.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Поиск по справочнику") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = onlyFavorites,
                    onClick = { onlyFavorites = !onlyFavorites },
                    label = { Text("★ Избранное") }
                )
            }
            items(KnowledgeRepository.categories) { item ->
                FilterChip(
                    selected = category == item,
                    onClick = { category = item },
                    label = { Text(item) }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(results, key = { it.id }) { item ->
                Card(
                    onClick = { onOpenArticle(item) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.category, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("• ${item.status}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(item.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (item.hasInteractiveDiagram || item.hasPneumaticSimulator || item.hasElectricalSimulator) {
                                Text("Интерактивный материал →", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            TextButton(onClick = { onToggleFavorite(item.id) }) {
                                Text(if (item.id in favoriteIds) "★" else "☆")
                            }
                        }
                    }
                }
            }
            if (results.isEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("Ничего не найдено. Попробуйте другой запрос.", modifier = Modifier.padding(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun KnowledgeArticleScreen(
    article: KnowledgeArticle,
    favoriteIds: Set<String>,
    onBack: () -> Unit,
    onOpenArticle: (KnowledgeArticle) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text("← К справочнику") }
        }
        item {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(article.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(4.dp))
                    Text("${article.category} • ${article.status}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = { onToggleFavorite(article.id) }) {
                    Text(if (article.id in favoriteIds) "★" else "☆", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        item {
            ArticleCard {
                Text(article.summary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
        }
        items(article.body) { paragraph ->
            Text(paragraph, style = MaterialTheme.typography.bodyLarge)
        }
        if (article.hasInteractiveDiagram) {
            item { Vl80InteractiveDiagram(onOpenArticle) }
        }
        if (article.hasAirRoute) {
            item { AirRouteCard(KnowledgeRepository.vl80ServiceBrakeRoute) }
        }
        if (article.hasPneumaticSimulator) {
            item { PneumaticSimulatorCard() }
        }
        if (article.hasElectricalSimulator) {
            item { ElectricalCircuitTrainer() }
        }
        if (article.relatedArticleIds.isNotEmpty()) {
            item {
                ArticleCard {
                    Text("Связанные материалы", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    article.relatedArticleIds.mapNotNull(KnowledgeRepository::articleById).forEach { related ->
                        TextButton(onClick = { onOpenArticle(related) }) { Text("→ ${related.title}") }
                    }
                }
            }
        }
        item {
            ArticleCard {
                Text("Источник", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(article.source.title, style = MaterialTheme.typography.bodyMedium)
                article.source.note?.let {
                    Spacer(Modifier.height(5.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                article.source.externalUrl?.let { url ->
                    Spacer(Modifier.height(10.dp))
                    Text("Для открытия внешнего источника требуется интернет.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, "Не найдено приложение для открытия ссылки", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text("Открыть первоисточник ↗")
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun AirRouteCard(route: AirRoute) {
    ArticleCard {
        Text(route.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text(route.start, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(5.dp))
        Text(route.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        route.steps.forEachIndexed { index, step ->
            Text("${index + 1}. ${step.title}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(step.description, style = MaterialTheme.typography.bodyMedium)
            if (index != route.steps.lastIndex) {
                Text("↓", modifier = Modifier.padding(vertical = 7.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun PneumaticSimulatorCard() {
    var scenarioIndex by rememberSaveable { mutableStateOf(0) }
    var stepIndex by rememberSaveable { mutableStateOf(0) }
    val scenarios = KnowledgeRepository.pneumaticScenarios
    val scenario = scenarios[scenarioIndex]
    val activeStep = scenario.steps[stepIndex]

    ArticleCard {
        Text("Путь воздуха", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Выберите режим и листайте маршрут по шагам. Всё работает офлайн.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(scenarios) { item ->
                FilterChip(
                    selected = item == scenario,
                    onClick = {
                        scenarioIndex = scenarios.indexOf(item)
                        stepIndex = 0
                    },
                    label = { Text(item.mode.title) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(scenario.summary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(scenario.start, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        PneumaticVisualRoute(scenario = scenario, activeStepIndex = stepIndex)
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Шаг ${stepIndex + 1} из ${scenario.steps.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(activeStep.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(activeStep.description, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { stepIndex = (stepIndex - 1).coerceAtLeast(0) },
                enabled = stepIndex > 0,
                modifier = Modifier.weight(1f)
            ) { Text("← Назад") }
            Button(
                onClick = { stepIndex = (stepIndex + 1).coerceAtMost(scenario.steps.lastIndex) },
                enabled = stepIndex < scenario.steps.lastIndex,
                modifier = Modifier.weight(1f)
            ) { Text("Дальше →") }
        }
    }
}

@Composable
private fun PneumaticVisualRoute(scenario: PneumaticScenario, activeStepIndex: Int) {
    var selectedComponent by remember { mutableStateOf<PneumaticComponent?>(null) }
    val horizontal = rememberScrollState()
    val paths = pneumaticPaths(scenario.mode)
    val routeColor = when (scenario.mode) {
        ru.railbrake.calculator.core.PneumaticMode.CHARGING -> Color(0xFF1976D2)
        ru.railbrake.calculator.core.PneumaticMode.SERVICE_BRAKE -> Color(0xFFE53935)
        ru.railbrake.calculator.core.PneumaticMode.RELEASE -> Color(0xFF00ACC1)
        ru.railbrake.calculator.core.PneumaticMode.AUXILIARY_BRAKE -> Color(0xFFFF8F00)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Интерактивная пневмосхема ВЛ80С", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Схему можно прокручивать. Нажмите на прибор, чтобы открыть пояснение.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().horizontalScroll(horizontal).background(Color.White, RoundedCornerShape(10.dp))) {
                Box(Modifier.width(1100.dp).aspectRatio(1181f / 573f)) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.vl80s_pneumatic_scheme),
                        contentDescription = "Пневматическая схема одной секции ВЛ80С",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                    Canvas(
                        Modifier.matchParentSize().pointerInput(scenario.mode) {
                            detectTapGestures { tap ->
                                val nx = tap.x / size.width
                                val ny = tap.y / size.height
                                selectedComponent = pneumaticComponents.lastOrNull {
                                    nx in it.left..it.right && ny in it.top..it.bottom
                                }
                            }
                        }
                    ) {
                        paths.forEachIndexed { index, path ->
                            if (index <= activeStepIndex) {
                                path.zipWithNext().forEach { (from, to) ->
                                    val a = Offset(from.x * size.width, from.y * size.height)
                                    val b = Offset(to.x * size.width, to.y * size.height)
                                    drawLine(Color.White.copy(alpha = 0.82f), a, b, strokeWidth = 11.dp.toPx())
                                    drawLine(routeColor, a, b, strokeWidth = 6.dp.toPx())
                                    val marker = Offset(a.x + (b.x - a.x) * 0.72f, a.y + (b.y - a.y) * 0.72f)
                                    drawCircle(routeColor, 5.dp.toPx(), marker)
                                    drawCircle(Color.White, 2.dp.toPx(), marker)
                                }
                            }
                        }
                        pneumaticComponents.forEach { component ->
                            drawRect(
                                color = routeColor.copy(alpha = 0.16f),
                                topLeft = Offset(component.left * size.width, component.top * size.height),
                                size = Size(
                                    (component.right - component.left) * size.width,
                                    (component.bottom - component.top) * size.height
                                ),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                "Цветная линия — пройденный маршрут; белая обводка отделяет его от линий исходной схемы. Точки показывают направление движения.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    selectedComponent?.let { component ->
        AlertDialog(
            onDismissRequest = { selectedComponent = null },
            confirmButton = { Button(onClick = { selectedComponent = null }) { Text("Закрыть") } },
            title = { Text(component.title) },
            text = { Text(component.details) }
        )
    }
}

private data class NormalizedPoint(val x: Float, val y: Float)

private data class PneumaticComponent(
    val title: String,
    val details: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

private val pneumaticComponents = listOf(
    PneumaticComponent("Главные резервуары РС1–РС3", "Три резервуара по 300 л на секцию. В них хранится основной запас сжатого воздуха.", 0.03f, 0.02f, 0.40f, 0.15f),
    PneumaticComponent("Компрессор КТ-6Эл", "Создаёт сжатый воздух и пополняет главные резервуары через охладитель, маслоотделитель и обратный клапан.", 0.65f, 0.05f, 0.79f, 0.31f),
    PneumaticComponent("Кран машиниста №395 и КВТ №254", "Кран №395 управляет давлением тормозной магистрали. КВТ №254 управляет тормозными цилиндрами локомотива и работает повторителем при автоматическом торможении.", 0.14f, 0.20f, 0.29f, 0.44f),
    PneumaticComponent("Воздухораспределитель №483", "Реагирует на изменение давления в тормозной магистрали и при торможении соединяет запасный резервуар с импульсной магистралью.", 0.39f, 0.27f, 0.54f, 0.45f),
    PneumaticComponent("Питательная магистраль", "Основная питающая линия электровоза. От неё получают воздух краны, реле давления и другие пневматические потребители.", 0.03f, 0.50f, 0.97f, 0.56f),
    PneumaticComponent("Импульсная магистраль", "Передаёт управляющее давление от воздухораспределителя к КВТ №254.", 0.03f, 0.57f, 0.97f, 0.63f),
    PneumaticComponent("Тормозная магистраль", "Изменение давления в ТМ управляет автоматическим торможением и отпуском.", 0.03f, 0.63f, 0.97f, 0.69f),
    PneumaticComponent("Реле давления №304", "По управляющему давлению наполняет тормозные цилиндры второй тележки воздухом из питательной магистрали.", 0.63f, 0.69f, 0.77f, 0.85f),
    PneumaticComponent("Тормозные цилиндры", "Преобразуют давление воздуха в механическое усилие тормозной рычажной передачи.", 0.20f, 0.78f, 0.86f, 0.98f)
)

private fun pneumaticPaths(mode: ru.railbrake.calculator.core.PneumaticMode): List<List<NormalizedPoint>> = when (mode) {
    ru.railbrake.calculator.core.PneumaticMode.CHARGING -> listOf(
        listOf(NormalizedPoint(.72f, .18f), NormalizedPoint(.72f, .06f), NormalizedPoint(.43f, .06f), NormalizedPoint(.43f, .09f), NormalizedPoint(.05f, .09f)),
        listOf(NormalizedPoint(.05f, .09f), NormalizedPoint(.05f, .53f), NormalizedPoint(.98f, .53f)),
        listOf(NormalizedPoint(.20f, .53f), NormalizedPoint(.20f, .33f), NormalizedPoint(.16f, .33f), NormalizedPoint(.16f, .66f), NormalizedPoint(.98f, .66f)),
        listOf(NormalizedPoint(.46f, .66f), NormalizedPoint(.46f, .36f))
    )
    ru.railbrake.calculator.core.PneumaticMode.SERVICE_BRAKE -> listOf(
        listOf(NormalizedPoint(.20f, .32f), NormalizedPoint(.16f, .32f), NormalizedPoint(.16f, .66f)),
        listOf(NormalizedPoint(.98f, .66f), NormalizedPoint(.46f, .66f), NormalizedPoint(.46f, .36f)),
        listOf(NormalizedPoint(.46f, .36f), NormalizedPoint(.46f, .61f), NormalizedPoint(.20f, .61f)),
        listOf(NormalizedPoint(.20f, .61f), NormalizedPoint(.20f, .33f)),
        listOf(NormalizedPoint(.20f, .53f), NormalizedPoint(.20f, .72f), NormalizedPoint(.29f, .72f), NormalizedPoint(.29f, .88f)),
        listOf(NormalizedPoint(.29f, .72f), NormalizedPoint(.70f, .72f), NormalizedPoint(.70f, .80f)),
        listOf(NormalizedPoint(.70f, .80f), NormalizedPoint(.82f, .88f))
    )
    ru.railbrake.calculator.core.PneumaticMode.RELEASE -> listOf(
        listOf(NormalizedPoint(.05f, .53f), NormalizedPoint(.20f, .53f), NormalizedPoint(.20f, .33f)),
        listOf(NormalizedPoint(.20f, .33f), NormalizedPoint(.16f, .33f), NormalizedPoint(.16f, .66f), NormalizedPoint(.98f, .66f)),
        listOf(NormalizedPoint(.46f, .66f), NormalizedPoint(.46f, .36f)),
        listOf(NormalizedPoint(.29f, .88f), NormalizedPoint(.29f, .72f), NormalizedPoint(.20f, .72f))
    )
    ru.railbrake.calculator.core.PneumaticMode.AUXILIARY_BRAKE -> listOf(
        listOf(NormalizedPoint(.05f, .53f), NormalizedPoint(.20f, .53f)),
        listOf(NormalizedPoint(.20f, .53f), NormalizedPoint(.20f, .33f)),
        listOf(NormalizedPoint(.20f, .33f), NormalizedPoint(.20f, .72f), NormalizedPoint(.29f, .72f), NormalizedPoint(.29f, .88f)),
        listOf(NormalizedPoint(.29f, .72f), NormalizedPoint(.70f, .72f), NormalizedPoint(.70f, .80f), NormalizedPoint(.82f, .88f))
    )
}

@Composable
private fun ArticleCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun Vl80InteractiveDiagram(onOpenArticle: (KnowledgeArticle) -> Unit) {
    var selected by remember { mutableStateOf<DiagramHotspot?>(null) }
    var showZones by rememberSaveable { mutableStateOf(true) }
    var tourIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val horizontal = rememberScrollState()
    val tourSpot = tourIndex?.let { KnowledgeRepository.vl80LayoutHotspots[it] }

    ArticleCard {
        Text("Интерактивная схема ВЛ80С", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Схема крупная и прокручивается по горизонтали. Нажмите на подсвеченную область.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Показывать зоны", modifier = Modifier.weight(1f))
            Switch(checked = showZones, onCheckedChange = { showZones = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { tourIndex = if (tourIndex == null) 0 else null },
                modifier = Modifier.weight(1f)
            ) { Text(if (tourIndex == null) "Начать экскурсию" else "Завершить") }
            if (tourIndex != null) {
                Button(
                    onClick = {
                        tourIndex = ((tourIndex ?: 0) + 1) % KnowledgeRepository.vl80LayoutHotspots.size
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Следующий узел →") }
            }
        }
        tourSpot?.let { spot ->
            Spacer(Modifier.height(8.dp))
            Text("Экскурсия: ${spot.title}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(spot.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(horizontal)
                .background(Color.White, RoundedCornerShape(12.dp))
        ) {
            Box(
                modifier = Modifier
                    .width(1050.dp)
                    .aspectRatio(1667f / 626f)
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.vl80s_layout_section1),
                    contentDescription = "Расположение оборудования ВЛ80С, секция 1",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val nx = offset.x / size.width.toFloat()
                                val ny = offset.y / size.height.toFloat()
                                selected = KnowledgeRepository.vl80LayoutHotspots.lastOrNull { it.contains(nx, ny) }
                            }
                        }
                ) {
                    if (showZones) {
                        KnowledgeRepository.vl80LayoutHotspots.forEach { spot ->
                            val x = spot.left * size.width
                            val y = spot.top * size.height
                            val w = (spot.right - spot.left) * size.width
                            val h = (spot.bottom - spot.top) * size.height
                            drawRect(
                                color = if (spot == tourSpot) Color(0x55FFB300) else Color(0x286EA8FE),
                                topLeft = Offset(x, y),
                                size = Size(w, h)
                            )
                            drawRect(
                                color = if (spot == tourSpot) Color(0xFFFF8F00) else Color(0xCC2962C7),
                                topLeft = Offset(x, y),
                                size = Size(w, h),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Нажмите на выделенные зоны: БСА №1/2, трансформатор, ВВК1/2, МВ3/4, МК, БУРТ, ФР, АЛСН, аппаратные панели и выпрямительные группы.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    selected?.let { spot ->
        AlertDialog(
            onDismissRequest = { selected = null },
            confirmButton = { Button(onClick = { selected = null }) { Text("Понятно") } },
            dismissButton = {
                TextButton(onClick = {
                    KnowledgeRepository.articleById("vl80-detail-${spot.id}")?.let(onOpenArticle)
                    selected = null
                }) { Text("Подробнее") }
            },
            title = { Text(spot.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(spot.subtitle, fontWeight = FontWeight.Bold)
                    Text(spot.details)
                }
            }
        )
    }
}
