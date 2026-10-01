package ru.railbrake.calculator.ui

import android.content.Context
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import ru.railbrake.calculator.core.TechnicalDataRepository
import ru.railbrake.calculator.core.TechnicalEntry
import ru.railbrake.calculator.core.TechnicalFamily
import kotlin.math.min

private data class SchemeNode(val key: String, val equipmentId: String, val label: String, val x: Float, val y: Float, val width: Float, val height: Float) {
    val center get() = Offset(x + width / 2f, y + height / 2f)
}
private data class SchemeEdge(val from: String, val to: String, val kind: String, val label: String)
private data class SchemeStep(val title: String, val explanation: String, val edges: List<SchemeEdge>)
private data class SchemeSource(val title: String, val details: String)
private data class SchemeSequence(
    val id: String, val family: TechnicalFamily, val sourceSchemeRef: String, val title: String,
    val disclaimer: String, val nodes: List<SchemeNode>, val steps: List<SchemeStep>,
    val sources: List<SchemeSource>, val left: Float, val top: Float, val right: Float, val bottom: Float
)
private data class FlowStyle(val label: String, val light: Color, val dark: Color, val dashed: Boolean)

private class StepwiseSchemeData(private val context: Context) {
    private fun asset(name: String) = JSONObject(context.assets.open("technical/$name").bufferedReader().use { it.readText() })
    private val palette = asset("scheme_semantic_palette.json")
    val styles: Map<String, FlowStyle> = buildMap {
        val list = palette.getJSONArray("flowTokens")
        for (i in 0 until list.length()) {
            val item = list.getJSONObject(i)
            val style = item.optString("lineStyle")
            put(item.getString("flowKind"), FlowStyle(item.getString("label"),
                item.getJSONObject("light").getString("stroke").asColor(),
                item.getJSONObject("dark").getString("stroke").asColor(),
                style.contains("dash", ignoreCase = true)))
        }
    }
    val sequences: List<SchemeSequence> = run {
        val layouts = asset("scheme_layout_metadata.json").getJSONArray("layouts")
        val byId = (0 until layouts.length()).associate { i ->
            layouts.getJSONObject(i).getString("sequenceId") to layouts.getJSONObject(i)
        }
        val flow = asset("stepwise_scheme_flows.json").getJSONArray("sequences")
        (0 until flow.length()).map { i ->
            val item = flow.getJSONObject(i)
            val layout = byId.getValue(item.getString("id"))
            val bounds = layout.getJSONObject("contentBounds")
            val rawNodes = layout.getJSONArray("nodes")
            val nodes = (0 until rawNodes.length()).map { j ->
                val node = rawNodes.getJSONObject(j)
                val pos = node.getJSONObject("position")
                SchemeNode(node.getString("nodeKey"), node.optString("equipmentId"), node.getString("label"),
                    pos.getDouble("x").toFloat(), pos.getDouble("y").toFloat(),
                    pos.getDouble("width").toFloat(), pos.getDouble("height").toFloat())
            }
            val rawSteps = item.getJSONArray("steps")
            val steps = (0 until rawSteps.length()).map { j ->
                val step = rawSteps.getJSONObject(j)
                val rawEdges = step.getJSONArray("edges")
                SchemeStep(step.getString("title"), step.optString("explanation"),
                    (0 until rawEdges.length()).map { k ->
                        val edge = rawEdges.getJSONObject(k)
                        SchemeEdge(edge.optString("fromAnchor").ifBlank { edge.getString("fromEquipmentId") },
                            edge.optString("toAnchor").ifBlank { edge.getString("toEquipmentId") },
                            edge.getString("flowKind"), edge.optString("label"))
                    })
            }
            val rawSources = item.getJSONArray("sourcePresentations")
            val sources = (0 until rawSources.length()).mapNotNull { j ->
                val source = rawSources.getJSONObject(j)
                source.optString("title").takeIf { it.isNotBlank() && it != "Источник схемы" }
                    ?.let { SchemeSource(it, source.optString("documentDetails")) }
            }.distinct()
            SchemeSequence(item.getString("id"), TechnicalFamily.valueOf(item.getString("family")),
                item.getString("sourceSchemeRef"), item.getString("title"), item.getString("functionalDisclaimer"),
                nodes, steps, sources, bounds.getDouble("left").toFloat(), bounds.getDouble("top").toFloat(),
                bounds.getDouble("right").toFloat(), bounds.getDouble("bottom").toFloat())
        }
    }
    fun forEntry(entry: TechnicalEntry): List<SchemeSequence> {
        val source = when (entry.id) {
            "CHME3-INT-PNEUMATIC" -> "CHME3-SCH-PNEUMATIC"
            "CHME3E-INT-START" -> "CHME3E-SCH-START-ELECTRONIC"
            else -> entry.id.replace("-INT-", "-SCH-")
        }
        val matching = sequences.filter { it.family == entry.family && it.sourceSchemeRef == source }
        if (matching.isNotEmpty() || !entry.family.isChme3 || entry.hotspots.isEmpty()) return matching
        // Other technical views have no verified edge route. Give them a compact,
        // zoomable equipment map without suggesting that the grid is a pipe/wire layout.
        val nodes = entry.hotspots.sortedWith(compareBy({ it.y }, { it.x })).mapIndexed { index, spot ->
            SchemeNode(spot.equipmentId, spot.equipmentId, spot.label,
                20f + (index % 3) * 210f, 20f + (index / 3) * 105f, 190f, 82f)
        }
        return listOf(SchemeSequence(entry.id, entry.family, entry.id, entry.title,
            "Функциональное расположение элементов; соединения и фактическое размещение оборудования здесь не показаны.",
            nodes, listOf(SchemeStep("Обзор оборудования", "Выберите элемент для открытия карточки.", emptyList())),
            emptyList(), 0f, 0f, 650f, 40f + ((nodes.size + 2) / 3) * 105f))
    }
}

private fun String.asColor(): Color = Color(android.graphics.Color.parseColor(this))

@Composable
internal fun StepwiseSchemeForEntry(
    entry: TechnicalEntry,
    repository: TechnicalDataRepository,
    onOpen: (TechnicalEntry) -> Unit
): Boolean {
    val context = LocalContext.current
    val data = remember(context) { StepwiseSchemeData(context.applicationContext) }
    val available = remember(entry.id, entry.family) { data.forEntry(entry) }
    if (available.isEmpty()) return false
    StepwiseSchemeViewer(available, data.styles, repository, entry, onOpen)
    return true
}

@Composable
private fun StepwiseSchemeViewer(
    sequences: List<SchemeSequence>, styles: Map<String, FlowStyle>,
    repository: TechnicalDataRepository, entry: TechnicalEntry, onOpen: (TechnicalEntry) -> Unit
) {
    var selectedId by rememberSaveable(entry.id) { mutableStateOf(sequences.first().id) }
    val sequence = sequences.firstOrNull { it.id == selectedId } ?: sequences.first()
    var stepIndex by rememberSaveable(sequence.id) { mutableIntStateOf(0) }
    val step = sequence.steps[stepIndex.coerceIn(sequence.steps.indices)]
    var selectedNode by rememberSaveable(sequence.id) { mutableStateOf<String?>(null) }
    val node = sequence.nodes.firstOrNull { it.key == selectedNode }
    val target = node?.equipmentId?.let { repository.entry(it, entry.family) }
    val isDark = MaterialTheme.colorScheme.background.red < 0.3f
    val background = MaterialTheme.colorScheme.surface
    val foreground = MaterialTheme.colorScheme.onSurface

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Интерактивная схема ${entry.family.title}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (sequences.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(sequences, key = { it.id }) { option ->
                    FilterChip(selected = option.id == sequence.id, onClick = { selectedId = option.id }, label = { Text(option.title) })
                }
            }
            Text(sequence.title, style = MaterialTheme.typography.titleMedium)
            Text(sequence.disclaimer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SchemeCanvas(sequence, stepIndex, styles, isDark, background, foreground,
                selectedNode, onSelect = { selectedNode = it })
            Text(if (sequence.steps.size == 1 && step.edges.isEmpty()) step.title
                else "Шаг ${stepIndex + 1} из ${sequence.steps.size}: ${step.title}", fontWeight = FontWeight.Bold)
            if (step.explanation.isNotBlank()) Text(step.explanation)
            step.edges.map { it.kind }.distinct().forEach { kind ->
                val style = styles[kind]
                if (style != null) Text("→ ${style.label}", color = if (isDark) style.dark else style.light)
            }
            if (sequence.steps.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { stepIndex-- }, enabled = stepIndex > 0, modifier = Modifier.weight(1f)) { Text("← Назад") }
                Button(onClick = { stepIndex++ }, enabled = stepIndex < sequence.steps.lastIndex, modifier = Modifier.weight(1f)) { Text("Дальше →") }
            }
            node?.let {
                Text("Выбрано: ${it.label}", fontWeight = FontWeight.Bold)
                if (target != null) OutlinedButton(onClick = { onOpen(target) }) { Text("Открыть карточку →") }
            }
            if (sequence.sources.isNotEmpty()) {
                Text("Источники", fontWeight = FontWeight.Bold)
                sequence.sources.forEach { source ->
                    Text(listOf(source.title, source.details).filter(String::isNotBlank).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun SchemeCanvas(
    sequence: SchemeSequence, stepIndex: Int, styles: Map<String, FlowStyle>, isDark: Boolean,
    background: Color, foreground: Color, selectedNode: String?, onSelect: (String) -> Unit
) {
    var zoom by rememberSaveable(sequence.id) { mutableFloatStateOf(1f) }
    var shiftX by rememberSaveable(sequence.id) { mutableFloatStateOf(0f) }
    var shiftY by rememberSaveable(sequence.id) { mutableFloatStateOf(0f) }
    val nodeByKey = remember(sequence.id) { sequence.nodes.associateBy { it.key } }
    val allEdges = remember(sequence.id) { sequence.steps.flatMap { it.edges }.distinct() }
    val activeEdges = sequence.steps.take(stepIndex + 1).flatMap { it.edges }.toSet()
    val activeNodes = activeEdges.flatMap { listOf(it.from, it.to) }.toSet()
    val boundsWidth = (sequence.right - sequence.left).coerceAtLeast(1f)
    val boundsHeight = (sequence.bottom - sequence.top).coerceAtLeast(1f)
    val centerX = (sequence.left + sequence.right) / 2f
    val centerY = (sequence.top + sequence.bottom) / 2f
    val accent = MaterialTheme.colorScheme.primary

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(370.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { 370.dp.toPx() }
            val paddingPx = with(density) { 24.dp.toPx() }
            val fit = min((widthPx - paddingPx * 2) / boundsWidth, (heightPx - paddingPx * 2) / boundsHeight)
            val viewportCenter = Offset(widthPx / 2, heightPx / 2)
            val transform = Modifier.fillMaxSize()
                .pointerInput(sequence.id, widthPx, heightPx) {
                    detectTransformGestures { centroid, pan, factor, _ ->
                        val next = (zoom * factor).coerceIn(1f, 5f)
                        val ratio = next / zoom
                        shiftX = (shiftX * ratio + pan.x + (centroid.x - viewportCenter.x) * (1f - ratio))
                            .coerceIn(-widthPx * next, widthPx * next)
                        shiftY = (shiftY * ratio + pan.y + (centroid.y - viewportCenter.y) * (1f - ratio))
                            .coerceIn(-heightPx * next, heightPx * next)
                        zoom = next
                    }
                }
                .pointerInput(sequence.id, widthPx, heightPx) {
                    detectTapGestures(onDoubleTap = { zoom = 1f; shiftX = 0f; shiftY = 0f }, onTap = { point ->
                        val x = (point.x - viewportCenter.x - shiftX) / (fit * zoom) + centerX
                        val y = (point.y - viewportCenter.y - shiftY) / (fit * zoom) + centerY
                        sequence.nodes.firstOrNull { x in it.x..(it.x + it.width) && y in it.y..(it.y + it.height) }
                            ?.let { onSelect(it.key) }
                    })
                }
            Canvas(transform.background(background)) {
                withTransform({
                    translate(viewportCenter.x + shiftX, viewportCenter.y + shiftY)
                    scale(fit * zoom, fit * zoom)
                    translate(-centerX, -centerY)
                }) {
                    allEdges.forEach { edge ->
                        val from = nodeByKey[edge.from]?.center ?: return@forEach
                        val to = nodeByKey[edge.to]?.center ?: return@forEach
                        val active = edge in activeEdges
                        val token = styles[edge.kind]
                        val color = if (active) (if (isDark) token?.dark else token?.light) ?: accent
                            else foreground.copy(alpha = 0.24f)
                        drawLine(color, from, to, strokeWidth = if (active) 5f else 2.5f)
                        val direction = to - from
                        val length = direction.getDistance().coerceAtLeast(1f)
                        val tip = from + direction * 0.70f
                        val unit = direction / length
                        val arrow = Path().apply {
                            moveTo(tip.x, tip.y)
                            lineTo(tip.x - unit.x * 16f - unit.y * 9f, tip.y - unit.y * 16f + unit.x * 9f)
                            lineTo(tip.x - unit.x * 16f + unit.y * 9f, tip.y - unit.y * 16f - unit.x * 9f)
                            close()
                        }
                        drawPath(arrow, color)
                    }
                    sequence.nodes.forEach { node ->
                        val active = node.key in activeNodes
                        val selected = node.key == selectedNode
                        val stroke = if (selected) accent else if (active) accent.copy(alpha = 0.8f) else foreground.copy(alpha = 0.42f)
                        drawRoundRect(color = if (isDark) Color(0xFF243237) else Color(0xFFF0F5F7),
                            topLeft = Offset(node.x, node.y), size = Size(node.width, node.height),
                            cornerRadius = CornerRadius(10f))
                        drawRoundRect(color = stroke, topLeft = Offset(node.x, node.y), size = Size(node.width, node.height),
                            cornerRadius = CornerRadius(10f), style = Stroke(if (selected) 4f else 2f))
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            textSize = 17f
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                            color = android.graphics.Color.parseColor(if (isDark) "#F0F6F5" else "#19242A")
                        }
                        val words = node.label.split(' ')
                        val lines = mutableListOf<String>()
                        var line = ""
                        words.forEach { word ->
                            val candidate = if (line.isEmpty()) word else "$line $word"
                            if (paint.measureText(candidate) > node.width - 16f && line.isNotEmpty()) {
                                lines += line; line = word
                            } else line = candidate
                        }
                        if (line.isNotEmpty()) lines += line
                        lines.take(3).forEachIndexed { index, text ->
                            drawContext.canvas.nativeCanvas.drawText(text, node.x + 8f,
                                node.y + (node.height - min(lines.size, 3) * 20f) / 2f + 17f + index * 20f, paint)
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { zoom = (zoom / 1.4f).coerceAtLeast(1f) }) { Text("−") }
            OutlinedButton(onClick = { zoom = (zoom * 1.4f).coerceAtMost(5f) }) { Text("+") }
            OutlinedButton(onClick = { zoom = 1f; shiftX = 0f; shiftY = 0f }) { Text("Показать целиком") }
        }
        Text("Разведите пальцы для увеличения, перемещайте схему жестом. Двойное касание возвращает общий вид.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
