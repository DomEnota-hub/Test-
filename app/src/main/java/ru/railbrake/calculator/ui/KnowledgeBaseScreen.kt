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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.railbrake.calculator.R
import ru.railbrake.calculator.core.AirRoute
import ru.railbrake.calculator.core.DiagramHotspot
import ru.railbrake.calculator.core.ExamQuestion
import ru.railbrake.calculator.core.ExamQuestionRepository
import ru.railbrake.calculator.core.KnowledgeArticle
import ru.railbrake.calculator.core.KnowledgeRepository
import ru.railbrake.calculator.core.PneumaticScenario
import ru.railbrake.calculator.data.FavoriteArticleRepository
import kotlin.math.sqrt

@Composable
fun KnowledgeBaseScreen() {
    var selectedArticleId by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val favoritesRepository = remember { FavoriteArticleRepository(context) }
    val examQuestionRepository = remember { ExamQuestionRepository(context) }
    var favoriteIds by remember { mutableStateOf(favoritesRepository.load()) }
    val article = selectedArticleId?.let(KnowledgeRepository::articleById)

    fun toggleFavorite(articleId: String) {
        favoriteIds = favoritesRepository.toggle(articleId)
    }

    if (article == null) {
        KnowledgeHome(
            favoriteIds = favoriteIds,
            examQuestionRepository = examQuestionRepository,
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
    examQuestionRepository: ExamQuestionRepository,
    onOpenArticle: (KnowledgeArticle) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Все") }
    var onlyFavorites by rememberSaveable { mutableStateOf(false) }
    val results = remember(query, category, onlyFavorites, favoriteIds) {
        KnowledgeRepository.search(query, category).filter { !onlyFavorites || it.id in favoriteIds }
    }
    val categories = remember {
        (KnowledgeRepository.categories + examQuestionRepository.categories).distinct()
    }
    val thematicFacts = remember(query, category, onlyFavorites) {
        if (onlyFavorites) emptyList() else examQuestionRepository.thematicFacts(
            query = query,
            category = category.takeUnless { it == "Все" }
        )
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
            items(categories) { item ->
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
            if (thematicFacts.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Проверочные сведения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Тематическая выборка из проверенной базы 329 вопросов. Формулировки экзаменационных ответов не заменяют действующие эксплуатационные документы.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(thematicFacts, key = { "fact-${it.id}" }) { fact ->
                    ThematicFactCard(fact)
                }
            }
            if (results.isEmpty() && thematicFacts.isEmpty()) {
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
private fun ThematicFactCard(item: ExamQuestion) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(item.category, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(item.question, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(item.correctAnswer, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Источник: ${item.blockTitle}, вопрос №${item.sourceNumber}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    val routeSteps = pneumaticRouteSteps(scenario.mode)
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
                                selectedComponent = pneumaticComponents
                                    .filter {
                                    nx in it.left..it.right && ny in it.top..it.bottom
                                    }
                                    .minByOrNull { (it.right - it.left) * (it.bottom - it.top) }
                            }
                        }
                    ) {
                        routeSteps.forEachIndexed { index, routeStep ->
                            if (index <= activeStepIndex) {
                                routeStep.segments.forEach { segment ->
                                    val points = segment.points.map { point ->
                                        Offset(
                                            point.x / PNEUMATIC_SCHEME_WIDTH * size.width,
                                            point.y / PNEUMATIC_SCHEME_HEIGHT * size.height
                                        )
                                    }
                                    val segmentColor = when (segment.kind) {
                                        PneumaticLinkKind.FLOW -> routeColor
                                        PneumaticLinkKind.RELEASE -> Color(0xFF1976D2)
                                        PneumaticLinkKind.CONTROL -> Color(0xFFFF8F00)
                                    }
                                    val pathEffect = if (segment.kind == PneumaticLinkKind.FLOW) null
                                    else PathEffect.dashPathEffect(floatArrayOf(14f, 9f))
                                    points.zipWithNext().forEach { (from, to) ->
                                        drawLine(segmentColor.copy(alpha = 0.30f), from, to, strokeWidth = 11.dp.toPx(), pathEffect = pathEffect)
                                        drawLine(segmentColor.copy(alpha = 0.96f), from, to, strokeWidth = 4.dp.toPx(), pathEffect = pathEffect)
                                    }
                                    drawRouteArrow(points, segmentColor)
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
                "Сплошная линия — поток воздуха; синяя пунктирная — разрядка/выпуск в атмосферу; оранжевая пунктирная — управляющее давление. Короткие участки выпуска внутри КМ, КВТ и РД показаны условно.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Выбор элемента без попадания по схеме",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(pneumaticComponents) { component ->
                    FilterChip(
                        selected = selectedComponent == component,
                        onClick = { selectedComponent = component },
                        label = { Text(component.title) }
                    )
                }
            }
        }
    }

    selectedComponent?.let { component ->
        AlertDialog(
            onDismissRequest = { selectedComponent = null },
            confirmButton = { Button(onClick = { selectedComponent = null }) { Text("Закрыть") } },
            title = { Text(component.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(component.details)
                    component.principle.takeIf(String::isNotBlank)?.let { DiagramDetail("Как работает", it) }
                    component.faultSigns.takeIf(String::isNotBlank)?.let { DiagramDetail("Возможные признаки неисправности", it) }
                    component.checks.takeIf(String::isNotBlank)?.let { DiagramDetail("Что проверяют", it) }
                }
            }
        )
    }
}

private const val PNEUMATIC_SCHEME_WIDTH = 1181f
private const val PNEUMATIC_SCHEME_HEIGHT = 573f

private data class SchemePoint(val x: Float, val y: Float)

private enum class PneumaticLinkKind { FLOW, RELEASE, CONTROL }

private data class PneumaticRouteSegment(
    val points: List<SchemePoint>,
    val kind: PneumaticLinkKind
)

private data class PneumaticRouteStep(val segments: List<PneumaticRouteSegment>)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRouteArrow(points: List<Offset>, color: Color) {
    val longest = points.zipWithNext().maxByOrNull { (from, to) ->
        val dx = to.x - from.x
        val dy = to.y - from.y
        dx * dx + dy * dy
    } ?: return
    val (from, to) = longest
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = sqrt(dx * dx + dy * dy)
    if (length < 1f) return
    val ux = dx / length
    val uy = dy / length
    val tip = Offset(from.x + dx * 0.72f, from.y + dy * 0.72f)
    val arrowLength = 11.dp.toPx()
    val arrowWidth = 6.dp.toPx()
    val base = Offset(tip.x - ux * arrowLength, tip.y - uy * arrowLength)
    val left = Offset(base.x - uy * arrowWidth, base.y + ux * arrowWidth)
    val right = Offset(base.x + uy * arrowWidth, base.y - ux * arrowWidth)
    drawLine(Color.White.copy(alpha = 0.9f), tip, left, strokeWidth = 5.dp.toPx())
    drawLine(Color.White.copy(alpha = 0.9f), tip, right, strokeWidth = 5.dp.toPx())
    drawLine(color, tip, left, strokeWidth = 2.5.dp.toPx())
    drawLine(color, tip, right, strokeWidth = 2.5.dp.toPx())
}

private data class PneumaticComponent(
    val title: String,
    val details: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val principle: String = "",
    val faultSigns: String = "",
    val checks: String = ""
)

@Composable
private fun DiagramDetail(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

private val pneumaticComponents = listOf(
    PneumaticComponent("Главные резервуары РС1–РС3", "Три резервуара по 300 л на секцию. В них хранится основной запас сжатого воздуха.", 0.03f, 0.02f, 0.40f, 0.15f, "Компрессор наполняет соединённые резервуары через подготовку воздуха и обратный клапан; из них питается ПМ.", "Медленный набор или быстрое падение давления, частые включения компрессора, наличие воды и масла при продувке.", "Герметичность, продувку, крепление, срок освидетельствования, работу обратного и предохранительных клапанов."),
    PneumaticComponent("Компрессор КТ-6Эл", "Создаёт сжатый воздух и пополняет главные резервуары через охладитель, маслоотделитель и обратный клапан.", 0.65f, 0.05f, 0.79f, 0.31f, "Двухступенчатое сжатие с промежуточным охлаждением уменьшает температуру воздуха; регулятор давления циклически включает мотор-компрессор.", "Замедленный набор давления, перегрев, посторонний стук, повышенный унос масла или срабатывание защиты двигателя.", "Время наполнения, уровень масла, температуру, работу клапанов, охладителя, маслоотделителя и отсутствие утечек."),
    PneumaticComponent("Кран машиниста №395", "Управляет зарядкой, служебным и экстренным торможением и отпуском изменением давления в уравнительном резервуаре и тормозной магистрали.", 0.17f, 0.20f, 0.225f, 0.38f, "Уравнительная часть заставляет давление ТМ следовать за давлением УР; в тормозных положениях воздух УР и ТМ выпускается в атмосферу.", "Не выдерживает давление, самопроизвольная разрядка, неверный темп ликвидации сверхзарядки, слабая или затяжная реакция тормозов.", "Плотность, зарядное давление, темпы разрядки и отпуска, работу редуктора и стабилизатора по установленной пробе."),
    PneumaticComponent("КВТ №254", "Управляет независимым тормозом локомотива и работает повторителем управляющего давления от ВР №483.", 0.225f, 0.20f, 0.31f, 0.38f, "По положению ручки либо давлению импульсной магистрали дозирует наполнение ТЦ первой тележки и управляющей камеры РД №304.", "ТЦ не наполняются или не отпускают, давление не соответствует положению ручки, утечка или самопроизвольное торможение.", "Давление по положениям, ступенчатость, время наполнения и отпуска, плотность и взаимодействие с автоматическим тормозом."),
    PneumaticComponent("Воздухораспределитель №483", "Реагирует на изменение давления в тормозной магистрали и при торможении соединяет запасный резервуар с импульсной магистралью.", 0.39f, 0.27f, 0.54f, 0.45f, "При снижении давления ТМ формирует управляющее давление, при повышении заряжает запасный резервуар и переводит тормоз на отпуск.", "Не срабатывает на разрядку, самопроизвольное торможение, неотпуск, истощение или утечка запасного резервуара.", "Зарядку РС4, чувствительность и время действия, отпуск, плотность и правильность включения режимов."),
    PneumaticComponent("Питательная магистраль", "Основная питающая линия электровоза. От неё получают воздух краны, реле давления и другие пневматические потребители.", 0.03f, 0.50f, 0.97f, 0.56f, "Получает воздух из главных резервуаров; сама не является командной линией, а обеспечивает запас для исполнительных приборов.", "Падение давления, частые включения компрессора, медленное наполнение ТЦ и вспомогательных приводов, слышимая утечка.", "Давление и плотность, краны и соединения, межсекционные рукава, отсутствие пережатия и утечек."),
    PneumaticComponent("Импульсная магистраль", "Передаёт управляющее давление от воздухораспределителя к КВТ №254.", 0.33f, 0.36f, 0.46f, 0.58f, "Это локальная управляющая ветвь, а не сквозная магистраль вдоль всей схемы.", "Ослабленное или отсутствующее автоматическое торможение локомотива, медленный отпуск, утечка.", "Плотность трубопровода и соединений, прохождение управляющего давления от ВР к КВТ."),
    PneumaticComponent("Тормозная магистраль", "Изменение давления в ТМ управляет автоматическим торможением и отпуском.", 0.03f, 0.63f, 0.97f, 0.69f, "КМ №395 заряжает ТМ и понижает её давление; воздухораспределители воспринимают темп и величину изменения как команду.", "Не выдерживает давление, медленная зарядка, неравномерное торможение поезда, самопроизвольное срабатывание или затяжной отпуск.", "Плотность и проходимость, зарядное давление, краны и рукава, темп разрядки, целостность концевых соединений."),
    PneumaticComponent("Реле давления №304", "По управляющему давлению наполняет тормозные цилиндры второй тележки воздухом из питательной магистрали.", 0.63f, 0.69f, 0.77f, 0.85f, "Повторяет управляющее давление большим расходом воздуха из ПМ; при отпуске соединяет ТЦ второй тележки с атмосферой.", "Разница давления или времени действия между тележками, неотпуск ТЦ второй тележки, утечка через выпуск.", "Давление и синхронность наполнения тележек, время отпуска, плотность управляющей камеры и выпуск в атмосферу."),
    PneumaticComponent("Тормозные цилиндры", "Преобразуют давление воздуха в механическое усилие тормозной рычажной передачи.", 0.20f, 0.78f, 0.86f, 0.98f, "Давление перемещает поршень и шток, рычажная передача прижимает колодки; возвратные элементы обеспечивают отпуск.", "Разное давление по тележкам, утечка, невыход или невозврат штока, неравномерный износ колодок, заторможенность после отпуска.", "Давление и время наполнения/отпуска, выход штока, плотность, рычажную передачу, колодки и свободный ход по нормам обслуживания.")
)

private fun pneumaticSegment(vararg points: Pair<Int, Int>): List<SchemePoint> =
    points.map { (x, y) -> SchemePoint(x.toFloat(), y.toFloat()) }

private fun pneumaticStep(vararg segments: List<SchemePoint>) =
    PneumaticRouteStep(segments.map { PneumaticRouteSegment(it, PneumaticLinkKind.FLOW) })

private fun pneumaticTypedStep(kind: PneumaticLinkKind, vararg segments: List<SchemePoint>) =
    PneumaticRouteStep(segments.map { PneumaticRouteSegment(it, kind) })

private val compressorToReservoirs = pneumaticSegment(
    718 to 101, 657 to 101, 641 to 101, 612 to 101, 594 to 101, 576 to 101,
    558 to 91, 558 to 59, 552 to 50, 542 to 50, 533 to 59, 533 to 103,
    524 to 112, 514 to 112, 505 to 103, 505 to 58, 497 to 49, 486 to 49,
    477 to 58, 477 to 102, 468 to 111, 458 to 111, 449 to 102, 449 to 66, 438 to 66
)

private val gr1ToGr2 = pneumaticSegment(360 to 66, 307 to 66)
private val gr2ToGr3 = pneumaticSegment(207 to 66, 154 to 66)

private val reservoirsToPm = pneumaticSegment(
    68 to 66, 56 to 74, 56 to 277, 92 to 277, 92 to 298
)

private val pmToKm395 = pneumaticSegment(
    92 to 298, 221 to 298, 221 to 239, 219 to 239, 219 to 176
)

private val km395ToTmRight = pneumaticSegment(
    244 to 176, 244 to 369, 1137 to 369
)

private val km395ToTmLeft = pneumaticSegment(
    244 to 176, 244 to 369, 39 to 369
)

private val km395ToEqualizingReservoir = pneumaticSegment(
    225 to 176, 204 to 176, 204 to 196
)

private val km395ToAtmosphere = pneumaticSegment(219 to 176, 219 to 205)

private val tmRightToAirDistributor = pneumaticSegment(
    1137 to 369, 518 to 369, 518 to 220, 532 to 220
)

private val tmLeftToAirDistributor = pneumaticSegment(
    39 to 369, 518 to 369
)

private val auxiliaryReservoirToAirDistributor = pneumaticSegment(
    608 to 235, 574 to 235, 574 to 220
)

private val airDistributorToKvt = pneumaticSegment(
    532 to 220, 518 to 220, 518 to 323, 389 to 323, 389 to 163, 289 to 163
)

private val pmToKvt = pneumaticSegment(
    92 to 298, 221 to 298, 221 to 239, 270 to 239, 270 to 185, 285 to 185
)

private val kvtToFirstBogie = pneumaticSegment(
    287 to 185, 287 to 349, 716 to 349, 716 to 468, 303 to 468, 289 to 458
)

private val kvtToPressureRelay = pneumaticSegment(
    287 to 349, 716 to 349, 716 to 432, 811 to 432, 811 to 414
)

private val pmToPressureRelay = pneumaticSegment(
    92 to 298, 451 to 298, 451 to 414, 811 to 414
)

private val pressureRelayToSecondBogie = pneumaticSegment(
    811 to 414, 811 to 466, 878 to 466, 891 to 458
)

// Выпуск происходит внутри приборов; короткие пунктирные стрелки показывают его условно.
private val kvtToAtmosphere = pneumaticSegment(287 to 185, 287 to 214)
private val pressureRelayToAtmosphere = pneumaticSegment(811 to 414, 811 to 389)

private fun pneumaticRouteSteps(mode: ru.railbrake.calculator.core.PneumaticMode): List<PneumaticRouteStep> = when (mode) {
    ru.railbrake.calculator.core.PneumaticMode.CHARGING -> listOf(
        pneumaticStep(compressorToReservoirs, gr1ToGr2, gr2ToGr3),
        pneumaticStep(reservoirsToPm),
        pneumaticStep(pmToKm395, km395ToTmLeft, km395ToTmRight),
        pneumaticStep(tmLeftToAirDistributor, tmRightToAirDistributor, auxiliaryReservoirToAirDistributor.reversed())
    )
    ru.railbrake.calculator.core.PneumaticMode.SERVICE_BRAKE -> listOf(
        pneumaticStep(gr1ToGr2, gr2ToGr3, reservoirsToPm),
        pneumaticTypedStep(PneumaticLinkKind.RELEASE, km395ToEqualizingReservoir.reversed(), km395ToAtmosphere),
        pneumaticTypedStep(PneumaticLinkKind.RELEASE, km395ToTmLeft.reversed(), km395ToTmRight.reversed(), km395ToAtmosphere),
        pneumaticTypedStep(PneumaticLinkKind.CONTROL, tmLeftToAirDistributor, tmRightToAirDistributor),
        pneumaticStep(auxiliaryReservoirToAirDistributor.reversed(), airDistributorToKvt),
        pneumaticStep(pmToKvt, kvtToFirstBogie),
        PneumaticRouteStep(
            listOf(
                PneumaticRouteSegment(kvtToPressureRelay, PneumaticLinkKind.CONTROL),
                PneumaticRouteSegment(pmToPressureRelay, PneumaticLinkKind.FLOW),
                PneumaticRouteSegment(pressureRelayToSecondBogie, PneumaticLinkKind.FLOW)
            )
        )
    )
    ru.railbrake.calculator.core.PneumaticMode.RELEASE -> listOf(
        pneumaticStep(pmToKm395),
        pneumaticStep(km395ToTmLeft, km395ToTmRight),
        pneumaticTypedStep(PneumaticLinkKind.CONTROL, tmLeftToAirDistributor, tmRightToAirDistributor),
        pneumaticTypedStep(PneumaticLinkKind.RELEASE, kvtToFirstBogie.reversed(), pressureRelayToSecondBogie.reversed(), kvtToAtmosphere, pressureRelayToAtmosphere)
    )
    ru.railbrake.calculator.core.PneumaticMode.AUXILIARY_BRAKE -> listOf(
        pneumaticStep(gr1ToGr2, gr2ToGr3, reservoirsToPm),
        pneumaticStep(pmToKvt),
        pneumaticStep(kvtToFirstBogie),
        PneumaticRouteStep(
            listOf(
                PneumaticRouteSegment(kvtToPressureRelay, PneumaticLinkKind.CONTROL),
                PneumaticRouteSegment(pmToPressureRelay, PneumaticLinkKind.FLOW),
                PneumaticRouteSegment(pressureRelayToSecondBogie, PneumaticLinkKind.FLOW)
            )
        )
    )
}

internal fun pneumaticRouteStepCounts(): List<Int> =
    ru.railbrake.calculator.core.PneumaticMode.entries.map { pneumaticRouteSteps(it).size }

internal fun allPneumaticRoutePointsFitSourceImage(): Boolean =
    ru.railbrake.calculator.core.PneumaticMode.entries
        .flatMap(::pneumaticRouteSteps)
        .flatMap(PneumaticRouteStep::segments)
        .all { segment ->
            segment.points.size >= 2 && segment.points.all { point ->
                point.x in 0f..PNEUMATIC_SCHEME_WIDTH && point.y in 0f..PNEUMATIC_SCHEME_HEIGHT
            }
        }

internal fun pneumaticRoutesExposeFlowReleaseAndControl(): Boolean {
    val serviceKinds = pneumaticRouteSteps(ru.railbrake.calculator.core.PneumaticMode.SERVICE_BRAKE)
        .flatMap(PneumaticRouteStep::segments)
        .map(PneumaticRouteSegment::kind)
        .toSet()
    val releaseKinds = pneumaticRouteSteps(ru.railbrake.calculator.core.PneumaticMode.RELEASE)
        .flatMap(PneumaticRouteStep::segments)
        .map(PneumaticRouteSegment::kind)
        .toSet()
    return serviceKinds == PneumaticLinkKind.entries.toSet() &&
        PneumaticLinkKind.RELEASE in releaseKinds && PneumaticLinkKind.CONTROL in releaseKinds
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
                                selected = KnowledgeRepository.vl80LayoutHotspots
                                    .filter { it.contains(nx, ny) }
                                    .minByOrNull { (it.right - it.left) * (it.bottom - it.top) }
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
        Spacer(Modifier.height(8.dp))
        Text("Выбор элемента списком", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(KnowledgeRepository.vl80LayoutHotspots) { spot ->
                FilterChip(
                    selected = selected == spot,
                    onClick = { selected = spot },
                    label = { Text(spot.title) }
                )
            }
        }
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
