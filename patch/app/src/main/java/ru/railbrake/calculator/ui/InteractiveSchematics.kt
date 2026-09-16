package ru.railbrake.calculator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.railbrake.calculator.core.PneumaticMode
import ru.railbrake.calculator.core.PneumaticScenario
import kotlin.math.sqrt

private data class CircuitInfo(
    val title: String,
    val subtitle: String,
    val purpose: String,
    val triggeredBy: String,
    val affects: String,
    val links: String,
    val principle: String = "",
    val faultSigns: String = "",
    val checks: String = ""
)

private data class CircuitNode(
    val id: String,
    val info: CircuitInfo,
    val x: Float,
    val y: Float,
    val width: Float = 182f,
    val height: Float = 82f
)

private data class CircuitEdge(
    val from: String,
    val to: String,
    val activationStep: Int
)

private data class CircuitStep(
    val title: String,
    val description: String
)

private data class CircuitScenario(
    val title: String,
    val summary: String,
    val nodes: List<CircuitNode>,
    val edges: List<CircuitEdge>,
    val steps: List<CircuitStep>,
    val color: Color
)

@Composable
internal fun PneumaticInteractiveDiagram(scenario: PneumaticScenario, activeStepIndex: Int) {
    val diagram = pneumaticDiagram(scenario.mode)
    CircuitDiagram(
        title = "Масштабируемая пневмосхема ВЛ80С",
        hint = "Все подписи отрисовываются приложением и остаются чёткими при увеличении. Нажмите на любой блок.",
        scenario = diagram,
        activeStepIndex = activeStepIndex,
        canvasWidth = 1310f,
        canvasHeight = 590f
    )
}

@Composable
internal fun ElectricalCircuitTrainer() {
    var scenarioIndex by rememberSaveable { mutableStateOf(0) }
    var stepIndex by rememberSaveable { mutableStateOf(0) }
    val scenario = electricalScenarios[scenarioIndex]

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Интерактивные электрические цепи ВЛ80С", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Это функциональная учебная схема: она показывает логику связей, а не заменяет заводской монтажный чертёж и номера проводов конкретной секции.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(electricalScenarios.size) { index ->
                    FilterChip(
                        selected = scenarioIndex == index,
                        onClick = {
                            scenarioIndex = index
                            stepIndex = 0
                        },
                        label = { Text(electricalScenarios[index].title) }
                    )
                }
            }
            Text(scenario.summary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            CircuitDiagram(
                title = "${scenario.title}: путь команды и энергии",
                hint = "Цветом показана цепь до выбранного шага. Серые связи ещё не задействованы.",
                scenario = scenario,
                activeStepIndex = stepIndex,
                canvasWidth = 1120f,
                canvasHeight = scenario.nodes.maxOf { it.y + it.height } + 28f
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Шаг ${stepIndex + 1} из ${scenario.steps.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(scenario.steps[stepIndex].title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(scenario.steps[stepIndex].description, style = MaterialTheme.typography.bodyMedium)
                }
            }
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
            Text(
                "ВЛ80С выпускался с изменениями схемы. Обозначения и блокировки всегда сверяйте со схемой конкретного номера и после ремонта.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CircuitDiagram(
    title: String,
    hint: String,
    scenario: CircuitScenario,
    activeStepIndex: Int,
    canvasWidth: Float,
    canvasHeight: Float
) {
    var selected by rememberSaveable(title) { mutableStateOf<String?>(null) }
    val selectedNode = scenario.nodes.firstOrNull { it.id == selected }
    val horizontal = rememberScrollState()
    val activeEdges = scenario.edges.filter { it.activationStep <= activeStepIndex }
    val activeNodeIds = activeEdges.flatMap { listOf(it.from, it.to) }.toSet()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(horizontal)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
            ) {
                Box(Modifier.width(canvasWidth.dp).height(canvasHeight.dp)) {
                    Canvas(Modifier.size(canvasWidth.dp, canvasHeight.dp)) {
                        scenario.edges.forEach { edge ->
                            val from = scenario.nodes.first { it.id == edge.from }
                            val to = scenario.nodes.first { it.id == edge.to }
                            val start = Offset((from.x + from.width / 2f).dp.toPx(), (from.y + from.height / 2f).dp.toPx())
                            val end = Offset((to.x + to.width / 2f).dp.toPx(), (to.y + to.height / 2f).dp.toPx())
                            val isActive = edge.activationStep <= activeStepIndex
                            val color = if (isActive) scenario.color else Color(0xFF78909C).copy(alpha = 0.36f)
                            drawLine(Color.White.copy(alpha = 0.85f), start, end, strokeWidth = 9.dp.toPx())
                            drawLine(color, start, end, strokeWidth = if (isActive) 5.dp.toPx() else 3.dp.toPx())
                            drawCircuitArrow(start, end, color, isActive)
                        }
                    }
                    scenario.nodes.forEach { node ->
                        val active = node.id in activeNodeIds
                        Card(
                            onClick = { selected = node.id },
                            modifier = Modifier
                                .offset(node.x.dp, node.y.dp)
                                .size(node.width.dp, node.height.dp)
                                .border(
                                    width = if (active) 2.dp else 1.dp,
                                    color = if (active) scenario.color else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(13.dp)
                                ),
                            shape = RoundedCornerShape(13.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (active) scenario.color.copy(alpha = 0.17f) else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(node.info.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                Text(node.info.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            Text("Стрелки показывают направление энергии, команды или защитного воздействия. Это функциональная учебная схема; блоки и пояснения доступны без интернета.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    selectedNode?.let { node ->
        AlertDialog(
            onDismissRequest = { selected = null },
            confirmButton = { Button(onClick = { selected = null }) { Text("Закрыть") } },
            title = { Text(node.info.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    DetailLine("Назначение", node.info.purpose)
                    DetailLine("Что приводит в действие", node.info.triggeredBy)
                    DetailLine("На что влияет", node.info.affects)
                    DetailLine("Связан с", node.info.links)
                    if (node.info.principle.isNotBlank()) DetailLine("Как работает", node.info.principle)
                    if (node.info.faultSigns.isNotBlank()) DetailLine("Возможные признаки неисправности", node.info.faultSigns)
                    if (node.info.checks.isNotBlank()) DetailLine("Что проверяют", node.info.checks)
                }
            }
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCircuitArrow(
    start: Offset,
    end: Offset,
    color: Color,
    active: Boolean
) {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = sqrt(dx * dx + dy * dy)
    if (length < 1f) return
    val ux = dx / length
    val uy = dy / length
    val tip = Offset(start.x + dx * 0.68f, start.y + dy * 0.68f)
    val arrowLength = (if (active) 12.dp else 9.dp).toPx()
    val arrowWidth = (if (active) 7.dp else 5.dp).toPx()
    val base = Offset(tip.x - ux * arrowLength, tip.y - uy * arrowLength)
    val left = Offset(base.x - uy * arrowWidth, base.y + ux * arrowWidth)
    val right = Offset(base.x + uy * arrowWidth, base.y - ux * arrowWidth)
    drawLine(Color.White.copy(alpha = 0.9f), tip, left, strokeWidth = 5.dp.toPx())
    drawLine(Color.White.copy(alpha = 0.9f), tip, right, strokeWidth = 5.dp.toPx())
    drawLine(color, tip, left, strokeWidth = 2.5.dp.toPx())
    drawLine(color, tip, right, strokeWidth = 2.5.dp.toPx())
}

@Composable
private fun DetailLine(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun info(
    title: String,
    subtitle: String,
    purpose: String,
    triggeredBy: String,
    affects: String,
    links: String,
    principle: String = "",
    faultSigns: String = "",
    checks: String = ""
) = CircuitInfo(title, subtitle, purpose, triggeredBy, affects, links, principle, faultSigns, checks)

private val pneumaticInfo = mapOf(
    "compressor" to info("Компрессор КТ-6Эл", "Источник сжатого воздуха", "Сжимает воздух и пополняет главные резервуары.", "Включается системой управления мотор-компрессором по команде регулятора давления.", "Создаёт запас воздуха для тормозов и вспомогательных пневмоцепей.", "Охладитель, маслоотделитель, обратный клапан, ГР."),
    "airPrep" to info("Охладитель и маслоотделитель", "Подготовка воздуха", "Охлаждает сжатый воздух и отделяет масло и влагу перед резервуарами.", "Воздушный поток от компрессора.", "Защищает резервуары и аппаратуру от конденсата и масла.", "Компрессор, обратный клапан, главные резервуары."),
    "gr" to info("Главные резервуары РС1–РС3", "3 × 300 л на секцию", "Хранят основной запас сжатого воздуха.", "Наполняются компрессором.", "Питают ПМ, тормозные приборы и вспомогательные потребители.", "Компрессор, ПМ, регулятор давления."),
    "pm" to info("Питательная магистраль", "Запас высокого давления", "Передаёт воздух от главных резервуаров к приборам и исполнительным устройствам.", "Давление создаётся компрессором и ГР.", "Питает КМ №395, КВТ №254, РД №304 и другие потребители.", "ГР, КМ №395, КВТ №254, РД №304."),
    "km" to info("Кран машиниста №395", "Управление автотормозами", "Изменяет давление в УР и заставляет ТМ повторять заданное изменение.", "Положение ручки крана задаёт машинист.", "Управляет зарядкой, торможением и отпуском через ТМ.", "ПМ, УР, ТМ, стабилизатор, редуктор."),
    "ur" to info("Уравнительный резервуар", "Задатчик давления", "Создаёт небольшую управляющую ёмкость, по которой кран регулирует давление ТМ.", "Заряжается или разряжается краном №395.", "Задаёт уровень давления для уравнительной части крана.", "КМ №395 и ТМ через уравнительную часть."),
    "tm" to info("Тормозная магистраль", "Командная магистраль поезда", "Передаёт пневматическую команду на торможение или отпуск по составу.", "КМ №395 понижает или повышает давление.", "Переключает воздухораспределитель №483 и тормоза вагонов.", "КМ №395, ВР №483, межсекционные соединения."),
    "vr" to info("Воздухораспределитель №483", "Распознаёт изменение ТМ", "По снижению давления ТМ формирует управляющее давление торможения; при повышении переводит систему на отпуск.", "Темп и величина изменения давления в ТМ.", "Импульсную магистраль и заряд запасного резервуара.", "ТМ, запасный резервуар, импульсная магистраль."),
    "zr" to info("Запасный резервуар РС4", "Местный запас торможения", "Хранит воздух, который ВР использует для создания управляющего давления.", "Заряжается через ВР при зарядном давлении ТМ.", "Питает управляющую цепь автоматического тормоза.", "ВР №483 и ТМ."),
    "impulse" to info("Импульсная магистраль", "Управляющее давление", "Передаёт небольшое управляющее давление от ВР к КВТ.", "Наполняется ВР №483 при автоматическом торможении.", "Заставляет КВТ работать повторителем.", "ВР №483, КЭБ, КВТ №254."),
    "kvt" to info("КВТ №254", "Тормоз локомотива и повторитель", "Дозирует давление независимого тормоза и повторяет команду ВР при автоматическом торможении.", "Ручка КВТ либо давление импульсной магистрали.", "Наполняет ТЦ первой тележки и управляющую камеру РД №304.", "ПМ, импульсная магистраль, БТ №367М, РД №304."),
    "bt" to info("Блокировка тормозов №367М", "Переключение кабины", "Обеспечивает правильное включение тормозных приборов только из рабочей кабины.", "Положение съёмной рукоятки блокировки.", "Разрешает или перекрывает путь к тормозным цилиндрам первой тележки.", "КВТ №254, ТЦ первой тележки."),
    "rd" to info("Реле давления №304", "Повторитель давления", "По давлению в управляющей камере открывает питание ТЦ второй тележки из ПМ.", "Управляющее давление от КВТ №254.", "Наполнение и выпуск ТЦ второй тележки.", "КВТ №254, ПМ, ТЦ второй тележки, атмосфера."),
    "tc1" to info("ТЦ первой тележки", "Исполнитель тормоза", "Преобразуют давление воздуха в усилие тормозной рычажной передачи.", "Наполняются через КВТ и БТ №367М.", "Прижимают тормозные колодки первой тележки.", "БТ №367М, КВТ №254, атмосфера."),
    "tc2" to info("ТЦ второй тележки", "Исполнитель тормоза", "Преобразуют давление воздуха в усилие тормозной рычажной передачи.", "Наполняются из ПМ через РД №304.", "Прижимают тормозные колодки второй тележки.", "РД №304, ПМ, атмосфера."),
    "atmosphere" to info("Атмосфера", "Выпуск воздуха", "При отпуске принимает воздух из управляющих камер и тормозных цилиндров.", "Выпускные клапаны открываются приборами в отпускном положении.", "Снижает давление и отпускает тормоза.", "КМ №395, ВР №483, КВТ №254, РД №304.")
)

private fun pNode(id: String, x: Float, y: Float) = CircuitNode(id, pneumaticInfo.getValue(id), x, y)

private val pneumaticNodes = listOf(
    pNode("compressor", 20f, 30f), pNode("airPrep", 225f, 30f), pNode("gr", 430f, 30f), pNode("pm", 635f, 30f),
    pNode("ur", 20f, 175f), pNode("km", 225f, 175f), pNode("tm", 430f, 175f), pNode("vr", 635f, 175f), pNode("zr", 840f, 175f),
    pNode("tc1", 20f, 320f), pNode("bt", 225f, 320f), pNode("kvt", 430f, 320f), pNode("impulse", 635f, 320f), pNode("atmosphere", 1045f, 320f),
    pNode("rd", 635f, 465f), pNode("tc2", 840f, 465f)
)

private fun pneumaticDiagram(mode: PneumaticMode): CircuitScenario {
    val color = when (mode) {
        PneumaticMode.CHARGING -> Color(0xFF1976D2)
        PneumaticMode.SERVICE_BRAKE -> Color(0xFFE53935)
        PneumaticMode.RELEASE -> Color(0xFF00ACC1)
        PneumaticMode.AUXILIARY_BRAKE -> Color(0xFFFF8F00)
    }
    val edges = when (mode) {
        PneumaticMode.CHARGING -> listOf(
            CircuitEdge("compressor", "airPrep", 0), CircuitEdge("airPrep", "gr", 0),
            CircuitEdge("gr", "pm", 1), CircuitEdge("pm", "km", 2), CircuitEdge("km", "tm", 2),
            CircuitEdge("tm", "vr", 3), CircuitEdge("vr", "zr", 3)
        )
        PneumaticMode.SERVICE_BRAKE -> listOf(
            CircuitEdge("gr", "pm", 0), CircuitEdge("ur", "km", 1), CircuitEdge("km", "atmosphere", 1),
            CircuitEdge("tm", "km", 2), CircuitEdge("km", "atmosphere", 2),
            CircuitEdge("tm", "vr", 3), CircuitEdge("zr", "vr", 4), CircuitEdge("vr", "impulse", 4), CircuitEdge("impulse", "kvt", 4),
            CircuitEdge("pm", "kvt", 5), CircuitEdge("kvt", "bt", 5), CircuitEdge("bt", "tc1", 5),
            CircuitEdge("kvt", "rd", 6), CircuitEdge("pm", "rd", 6), CircuitEdge("rd", "tc2", 6)
        )
        PneumaticMode.RELEASE -> listOf(
            CircuitEdge("gr", "pm", 0), CircuitEdge("pm", "km", 0), CircuitEdge("km", "tm", 1),
            CircuitEdge("tm", "vr", 2), CircuitEdge("vr", "atmosphere", 2), CircuitEdge("tc1", "kvt", 3),
            CircuitEdge("tc2", "rd", 3), CircuitEdge("kvt", "atmosphere", 3), CircuitEdge("rd", "atmosphere", 3)
        )
        PneumaticMode.AUXILIARY_BRAKE -> listOf(
            CircuitEdge("gr", "pm", 0), CircuitEdge("pm", "kvt", 1),
            CircuitEdge("kvt", "bt", 2), CircuitEdge("bt", "tc1", 2),
            CircuitEdge("kvt", "rd", 3), CircuitEdge("pm", "rd", 3), CircuitEdge("rd", "tc2", 3)
        )
    }
    return CircuitScenario(mode.title, "", pneumaticNodes, edges, emptyList(), color)
}

private val electricalInfo = mapOf(
    "contact" to info("Контактная сеть", "25 кВ, 50 Гц", "Источник однофазного переменного напряжения для электровоза.", "Питание присутствует на электрифицированном участке.", "Подаёт энергию на первичную цепь через токоприёмник.", "Токоприёмник, тяговая подстанция, рельсовая цепь возврата."),
    "pantograph" to info("Токоприёмник Л-13У1/Л-14М1", "Съём тока", "Соединяет крышевую высоковольтную шину с контактным проводом.", "Поднимается пневмоприводом при включении клапана 245 и выполнении блокировок.", "Подаёт 25 кВ к высоковольтному разъединителю и ГВ.", "Контактная сеть, клапан 245, РВН, главный выключатель.", "Сжатый воздух поднимает рамы, а пружинно-пневматическая система создаёт требуемое нажатие полоза. При снятии питания клапана привод выпускает воздух и токоприёмник опускается.", "Не поднимается или самопроизвольно опускается, искрение и отрыв полоза, ненормальный шум, повреждение вставок или рам.", "Давление воздуха, цепь клапана 245, состояние полоза и вставок, шарниров, шунтов и изоляторов; крышевое оборудование осматривают только по установленному безопасному порядку."),
    "rvn" to info("РВН-2", "Высоковольтный разъединитель", "Позволяет изолировать неисправный токоприёмник или участок высоковольтной цепи.", "Переключается только по установленному безопасному порядку.", "Разрывает цепь между токоприёмником и главным выключателем.", "Токоприёмник, главный выключатель."),
    "gv" to info("Главный выключатель ВОВ-25", "Коммутация и защита 25 кВ", "Подключает первичную обмотку трансформатора и отключает её при защитном воздействии.", "Команда включения разрешается цепями управления; отключение вызывают защита или команда машиниста.", "Снимает высокое напряжение с тягового трансформатора и силовых цепей секции.", "РВН, трансформатор тока, реле защиты, тяговый трансформатор.", "Высоковольтные контакты замыкаются пневматическим приводом после выполнения электрических и пневматических разрешений. При защитной команде удержание снимается, дуга гасится в дугогасительном устройстве.", "Не включается, отключается сразу или под нагрузкой, повторное срабатывание защиты, отсутствие подтверждения положения.", "Давление в резервуаре ГВ, цепи удерживающей и включающей катушек, блокировки, сигнализацию и причину срабатывания защиты. Повторное включение без выяснения причины недопустимо."),
    "transformer" to info("ОДЦЭ-5000/25Б", "Тяговый трансформатор", "Понижает 25 кВ и формирует тяговые обмотки и обмотку собственных нужд.", "Работает при поднятом токоприёмнике и включённом ГВ.", "Питает тяговую цепь, вспомогательные машины и часть низковольтных источников.", "ГВ, ЭКГ-8Ж, выпрямители, обмотка собственных нужд.", "Первичная обмотка принимает напряжение контактной сети; вторичные тяговые обмотки через ЭКГ питают выпрямители, отдельная обмотка — собственные нужды. Масло и принудительное охлаждение отводят тепло.", "Запах или дым, перегрев масла, течь, ненормальный гул, газовая или токовая защита, рост температуры при штатной нагрузке.", "Уровень и течи масла, состояние вводов и охлаждения, работу маслонасоса и вентиляторов, показания температуры и сработавшие защиты."),
    "ekg" to info("Главный контроллер ЭКГ-8Ж", "Ступенчатое регулирование", "Переключает секции регулирующей обмотки трансформатора и изменяет напряжение ТЭД.", "Серводвигатель получает команды от контроллера машиниста через блокировки.", "Определяет позицию и тяговое напряжение.", "КМ-84, серводвигатель ДМК-1/50, переходной реактор, трансформатор.", "Контакторы по заданной последовательности переключают выводы регулирующей обмотки. Переходной реактор ограничивает ток в момент перехода, а сельсины и блок-контакты подтверждают позицию.", "Застревание на позиции, рассогласование указателя, выбивание защиты при переходе, посторонний шум привода, невозможность набора или сброса позиций.", "Положение указателя и сельсинов, работу серводвигателя, цепи синхронизации секций, блок-контакты и отсутствие следов перегрева контакторов."),
    "rectifier" to info("ВУ 61/62 ВУК-4000Т-02", "Выпрямительные установки", "Преобразуют однофазный переменный ток тяговых обмоток в пульсирующий постоянный ток.", "Получают напряжение от трансформатора при собранной тяговой схеме.", "Питают две группы тяговых двигателей.", "Тяговый трансформатор, сглаживающие реакторы, разъединители 81/82.", "Плечи силовых вентилей проводят ток поочерёдно в нужном направлении; охлаждение ограничивает температуру полупроводников.", "Перегрев, срабатывание защиты, асимметрия токов групп, запах, дым или следы пробоя; тяга одной группы пропадает либо ограничивается.", "Работу вентиляции, токи групп, сигнализацию защит и состояние доступных соединений. Вскрытие и измерения выполняют только при снятом напряжении по технологии ремонта."),
    "reactor" to info("Сглаживающие реакторы РС-53", "Снижение пульсаций", "Сглаживают пульсации выпрямленного тока.", "Ток проходит при включённой тяговой цепи.", "Улучшает ток в цепях ТЭД и условия коммутации.", "ВУ 61/62, линейные контакторы, ТЭД."),
    "switchgear" to info("Контакторы 51–54 и реверсоры 63/64", "Подключение и направление", "Линейные контакторы подключают ТЭД, реверсоры меняют направление тока возбуждения.", "Команды контроллера машиниста при выполнении электрических и пневматических блокировок.", "Включение двигателей и направление движения.", "Сглаживающие реакторы, цепи управления, ТЭД."),
    "ted" to info("ТЭД НБ-418К6/К8", "Тяговые двигатели", "Преобразуют электрическую энергию в тяговый момент; при реостатном торможении работают генераторами.", "Ток поступает через ВУ, реакторы и контакторы.", "Создают тягу либо электрическое тормозное усилие.", "Редуктор колёсной пары, контакторы, реверсоры, тормозные резисторы.", "Последовательное возбуждение создаёт магнитный поток и вращающий момент. В реостатном торможении схема переключает двигатели в генераторный режим, а энергия рассеивается резисторами.", "Искрение, запах, дым, перегрев подшипников, ненормальный шум, вибрация, боксование одной оси, срабатывание реле перегрузки или заземления.", "Токи и их равномерность, вентиляцию, состояние щёточно-коллекторного узла по регламенту, кабели, подшипники и передачу. Конкретный порядок — по руководству данной модификации."),
    "battery" to info("Аккумуляторная батарея", "Резерв 50 В", "Питает цепи управления до появления питания от ТРПШ и при кратковременных провалах.", "Подключается рубильником; заряжается через ТРПШ-2 и выпрямительный мост.", "Цепи управления, сигнализацию и электропневматические вентили.", "Распределительный щит, ТРПШ-2, автоматы и предохранители."),
    "control50" to info("Распределительный щит 50 В", "Питание управления", "Распределяет низковольтное питание по автоматам и цепям управления.", "Получает питание от батареи или ТРПШ-2.", "Кнопки, реле, контакторы, вентили и сигнализацию.", "АБ, ТРПШ-2, ВА1–ВА14, аппараты управления."),
    "va1" to info("ВА1 «Токоприёмники»", "Защита цепи управления", "Подаёт защищённое питание в цепь управления токоприёмниками.", "Включается машинистом на блоке автоматов.", "Разрешает питание кнопок подъёма и реле 248.", "РЩ 50 В, кнопки токоприёмников, реле 248."),
    "buttons" to info("Кнопки токоприёмников", "Команда машиниста", "Выбирают передний или задний токоприёмник и формируют команду подъёма.", "Нажатие/включение машинистом при подготовленной схеме.", "Реле 248 и клапан выбранного токоприёмника.", "ВА1, межсекционные провода, реле 248."),
    "interlocks" to info("Блокировки ВВК и давление ГВ", "Условия безопасности", "Не допускают подъём токоприёмника при открытых дверях/шторах ВВК или недостаточном давлении.", "Состояние пневмоблокировок и контакт реле давления резервуара ГВ.", "Разрешают питание реле 248 и клапана 245.", "Вентиль защиты 104, ПБ1/ПБ2, РД ГВ, реле 248."),
    "valve104" to info("Вентиль защиты 104", "Пневматическая блокировка", "Пропускает воздух через блокировки закрытых дверей и штор ВВК.", "Получает 50 В при включённой общей кнопке токоприёмников.", "Обеспечивает пневматическое разрешение подъёма.", "РЩ 50 В, ПБ ВВК, клапан 245."),
    "relay248" to info("Реле 248", "Выбор и разрешение", "Коммутирует питание клапана токоприёмника и подготавливает цепи главного выключателя.", "Кнопки токоприёмников, межсекционные блокировки и контакт давления ГВ.", "Клапан 245, удерживающая и включающая катушки ГВ.", "Кнопки, РД ГВ, клапан 245, ГВ."),
    "valve245" to info("Клапан токоприёмника 245", "Электропневматический клапан", "Подаёт сжатый воздух в цилиндр привода токоприёмника.", "Получает 50 В через контакт реле 248.", "Поднимает выбранный токоприёмник.", "Реле 248, пневмопривод, токоприёмник."),
    "drive" to info("Пневмопривод токоприёмника", "Исполнитель", "Преобразует давление воздуха в подъём механизма.", "Воздух поступает через клапан 245.", "Прижимает полоз к контактному проводу.", "Клапан 245, токоприёмник, пружины опускания."),
    "auxWinding" to info("Обмотка собственных нужд", "232/406/638 В холостого хода", "Питает однофазных потребителей и систему получения трёхфазного напряжения.", "Напряжение появляется при включённом ГВ.", "Расщепитель фаз, отопление, ТРПШ и вспомогательные цепи.", "Тяговый трансформатор, ФР, ТРПШ-2, контакторы."),
    "phaseSplitter" to info("Фазорасщепитель НБ-455А", "Формирование трёх фаз", "Преобразует однофазное питание в трёхфазную систему для асинхронных вспомогательных двигателей.", "Запускается своей цепью управления после появления питания собственных нужд.", "Питает мотор-вентиляторы, насос и мотор-компрессор.", "Обмотка собственных нужд, ППРФ-300, трёхфазная шина.", "Асинхронная машина с пусковой схемой создаёт третью фазу; после разгона пусковая ветвь отключается и формируется трёхфазная шина собственных нужд.", "Не запускается, затяжной пуск, гул, повышенный ток, выпадение вспомогательных машин или срабатывание защиты.", "Напряжение собственных нужд, пусковую аппаратуру и реле оборотов, симметрию фаз и отсутствие перегрева."),
    "threePhase" to info("Трёхфазная шина", "Питание вспомогательных машин", "Распределяет сформированное трёхфазное напряжение.", "Появляется после запуска фазорасщепителя.", "Питает вспомогательные двигатели через контакторы и защиту.", "ФР, МВ1–МВ4, МН, МК."),
    "fans12" to info("МВ1 и МВ2", "Охлаждение ТЭД", "Охлаждают тяговые двигатели первой–четвёртой колёсных пар.", "Включаются контакторами цепей вспомогательных машин.", "Разрешают длительную работу ТЭД под нагрузкой.", "Трёхфазная шина, контакторы, реле оборотов."),
    "fans34" to info("МВ3 и МВ4", "Охлаждение силового оборудования", "В тяге охлаждают ВУ, реакторы и трансформатор; в торможении направляют воздух на тормозные резисторы.", "Включаются контакторами; направление воздуха меняет УПВ.", "Температурный режим ВУ, реакторов, трансформатора и резисторов.", "Трёхфазная шина, УПВ-5, силовое оборудование."),
    "oilPump" to info("Масляный насос МН", "Охлаждение трансформатора", "Обеспечивает циркуляцию масла через охладители тягового трансформатора.", "Включается цепью вспомогательных машин.", "Отводит тепло от обмоток трансформатора.", "Трёхфазная шина, трансформатор, система охлаждения."),
    "motorCompressor" to info("Мотор-компрессор МК", "Привод КТ-6Эл", "Вращает главный компрессор.", "Контактор включается по команде регулятора давления при готовой вспомогательной цепи.", "Пополняет главные резервуары сжатым воздухом.", "Трёхфазная шина, регулятор давления, компрессор КТ-6Эл.", "Электродвигатель вращает двухступенчатый компрессор; воздух проходит охлаждение, отделение масла и обратный клапан в главные резервуары. Регулятор давления задаёт цикл включения и отключения.", "Медленный набор давления, частые пуски, перегрев, повышенный шум, выброс масла, утечки или срабатывание защиты двигателя.", "Время наполнения резервуаров, давление включения/отключения, уровень масла, охлаждение, утечки и работу предохранительных и обратных клапанов."),
    "trpsh" to info("ТРПШ-2 и выпрямительный мост", "Источник цепей управления", "Преобразует питание собственных нужд в стабилизированное выпрямленное напряжение около 50–55 В.", "Получает 380 В от обмотки собственных нужд.", "Питает цепи управления и заряжает аккумуляторную батарею.", "Обмотка собственных нужд, РЩ, АБ, регулятор напряжения."),
    "km84" to info("Контроллер машиниста КМ-84", "Команда режима", "Формирует команды тяги, направления, ослабления возбуждения и электрического торможения.", "Управляется машинистом из активной кабины.", "Цепи контакторов, ЭКГ, БП и тормозные переключатели.", "РЩ 50 В, ЭКГ, БП, контакторы, межсекционные цепи."),
    "bp" to info("Блокировочный переключатель БП", "«Тяга / Торможение»", "Переключает цепи управления и вспомогательные цепи между тяговым и тормозным режимами.", "Катушки получают команду от контроллера машиниста.", "Разрешает соответствующие контакторы и питание аппаратуры торможения.", "КМ-84, переключатели 49/50, БУРТ, контакторы."),
    "brakeSwitch" to info("Тормозные переключатели 49/50", "Перекоммутация ТЭД", "Переводят силовые цепи двигателей из тяги в генераторный режим.", "Включаются цепью тормозного режима после установки БП.", "Подготавливают цепь возбуждения и исключают тяговое включение.", "БП, контакторы 46/47, ТЭД, линейные контакторы."),
    "excitation" to info("ВУ возбуждения 60", "Питание обмоток возбуждения", "Создаёт регулируемый ток возбуждения ТЭД при реостатном торможении.", "Питается через БП в положении «Торможение»; управляется аппаратурой БУРТ.", "Определяет магнитный поток и тормозной ток ТЭД.", "БУРТ, контакторы 46/47, обмотки возбуждения ТЭД."),
    "burt" to info("БУРТ / блок автоматики", "Регулирование торможения", "Управляет выпрямительной установкой возбуждения и поддерживает заданный режим реостатного торможения.", "Получает команду тормозной рукоятки и сигналы измерительных блоков.", "Ток возбуждения и величину электрического тормозного усилия.", "КМ-84, блок измерения, ВУ возбуждения 60, защиты.", "Блок сравнивает задание машиниста с сигналами тока и скорости, изменяет возбуждение ТЭД и ограничивает режим по защитным условиям.", "Тормозная схема не собирается, усилие не регулируется или срывается, появляются сигналы защиты и замещение пневматическим тормозом.", "Готовность переключателей 49/50, питание блока, сигналы измерительных цепей, охлаждение резисторов и конкретное срабатывание защиты."),
    "brakeRes" to info("Тормозные резисторы", "Рассеивание энергии", "Преобразуют электрическую энергию генераторного режима ТЭД в тепло.", "Подключаются тормозными переключателями и контакторами.", "Создают нагрузку ТЭД и требуют принудительного охлаждения.", "ТЭД, контакторы, МВ3/МВ4, УПВ."),
    "pvu" to info("ПВУ1/ПВУ2 и защита тормоза", "Связь с пневматикой", "Раздельные пневматические выключатели связывают электрическое торможение с давлением в тормозной системе и участвуют в ограничении совместного действия тормозов.", "Порог зависит от конкретной цепи: ориентировочное срабатывание при 1,3–1,5 кгс/см², обратное переключение — при снижении примерно до 0,5 кгс/см²; точные уставки проверяют по схеме и паспорту аппарата.", "Через цепи БУРТ и контакторов разрешают, прекращают либо замещают электрическое торможение пневматическим.", "Пневмотормоз, БУРТ, контакторы 46/47, тормозные цилиндры.", "Мембрана воспринимает давление и переключает электрические контакты. ПВУ1 и ПВУ2 выполняют разные функции, поэтому на монтажной схеме их нельзя считать одним аппаратом.", "Электрический тормоз не собирается, не разбирается при пневматическом торможении либо замещение происходит несвоевременно.", "Фактические давления срабатывания и отпускания, герметичность подвода воздуха, состояние контактов и соответствие цепей конкретному исполнению ВЛ80С."),
    "sensors" to info("ТТ, РМТ, РЗ и РП", "Датчики и реле защиты", "Выявляют сверхток, замыкание на землю и перегрузку тяговых двигателей.", "Аварийный ток или появление потенциала относительно корпуса.", "Формируют команду отключения силовой цепи.", "Первичная цепь, ТЭД, промежуточные реле, ГВ."),
    "protection" to info("Промежуточные реле защиты", "Логика отключения", "Собирают сигналы реле и передают адресную команду на отключение ГВ, отдельных линейных контакторов либо тормозной схемы.", "Срабатывание РМТ, РЗ, РП и других защит.", "Действие зависит от вида защиты: не каждая неисправность одновременно отключает ГВ и контакторы 51–54. Одновременно включается соответствующая сигнализация.", "Реле контроля, ГВ, контакторы 51–54, сигнальное табло.", "Защита локализует опасный режим на минимально необходимом уровне, заданном электрической схемой. Поэтому перед восстановлением сначала определяют, какое именно реле сработало.", "Повторное отключение при наборе позиции, выпадение отдельной группы ТЭД, сигнал на табло, невозможность восстановить схему.", "Сработавший аппарат и первопричину, токи групп, изоляцию и связанные цепи. Многократное восстановление без выяснения причины недопустимо."),
    "signal" to info("Сигнальное табло", "Информация машинисту", "Показывает срабатывание защит и состояние основных аппаратов.", "Контакты реле и блокировки соответствующих цепей.", "Действия машиниста по поиску причины и восстановлению.", "Реле защиты, цепи управления, кабина.")
)

private fun eNode(id: String, x: Float, y: Float) = CircuitNode(id, electricalInfo.getValue(id), x, y)

private val electricalScenarios = listOf(
    CircuitScenario(
        title = "Тяга",
        summary = "Упрощённый функциональный путь энергии к ТЭД. Трансформаторы тока, дроссель ДП, фильтр и часть коммутационных аппаратов на этом уровне условно опущены.",
        nodes = listOf(
            eNode("contact", 20f, 25f), eNode("pantograph", 225f, 25f), eNode("rvn", 430f, 25f), eNode("gv", 635f, 25f), eNode("transformer", 840f, 25f),
            eNode("ted", 20f, 175f), eNode("switchgear", 225f, 175f), eNode("reactor", 430f, 175f), eNode("rectifier", 635f, 175f), eNode("ekg", 840f, 175f),
            eNode("km84", 430f, 325f)
        ),
        edges = listOf(
            CircuitEdge("contact", "pantograph", 0), CircuitEdge("pantograph", "rvn", 0), CircuitEdge("rvn", "gv", 0),
            CircuitEdge("gv", "transformer", 1), CircuitEdge("km84", "ekg", 2), CircuitEdge("transformer", "ekg", 2),
            CircuitEdge("ekg", "rectifier", 3), CircuitEdge("rectifier", "reactor", 3),
            CircuitEdge("reactor", "switchgear", 4), CircuitEdge("switchgear", "ted", 4)
        ),
        steps = listOf(
            CircuitStep("Приём энергии 25 кВ", "Ток поступает с контактного провода через токоприёмник, РВН и замкнутый главный выключатель."),
            CircuitStep("Тяговый трансформатор", "ОДЦЭ-5000/25Б понижает напряжение и разделяет тяговые и собственные нужды."),
            CircuitStep("Выбор позиции", "КМ-84 через цепи управления задаёт перемещение ЭКГ-8Ж, который переключает секции регулирующей обмотки."),
            CircuitStep("Выпрямление и сглаживание", "ВУ 61/62 выпрямляют ток, а реакторы РС-53 уменьшают пульсации."),
            CircuitStep("Тяговые двигатели", "Контакторы 51–54 подключают ТЭД; реверсоры задают направление тока возбуждения и движения.")
        ),
        color = Color(0xFFE53935)
    ),
    CircuitScenario(
        title = "Подъём ТП",
        summary = "Электрическая команда 50 В проходит блокировки и включает пневматический привод выбранного токоприёмника.",
        nodes = listOf(
            eNode("battery", 20f, 25f), eNode("control50", 225f, 25f), eNode("va1", 430f, 25f), eNode("buttons", 635f, 25f), eNode("relay248", 840f, 25f),
            eNode("interlocks", 225f, 175f), eNode("valve104", 430f, 175f), eNode("valve245", 635f, 175f), eNode("drive", 840f, 175f), eNode("pantograph", 840f, 325f)
        ),
        edges = listOf(
            CircuitEdge("battery", "control50", 0), CircuitEdge("control50", "va1", 0), CircuitEdge("va1", "buttons", 1),
            CircuitEdge("buttons", "valve104", 2), CircuitEdge("interlocks", "valve104", 2), CircuitEdge("buttons", "relay248", 3),
            CircuitEdge("interlocks", "relay248", 3), CircuitEdge("relay248", "valve245", 4), CircuitEdge("valve104", "valve245", 4),
            CircuitEdge("valve245", "drive", 5), CircuitEdge("drive", "pantograph", 5)
        ),
        steps = listOf(
            CircuitStep("Источник управления", "Аккумуляторная батарея или ТРПШ-2 питают распределительный щит; ВА1 защищает цепь токоприёмников."),
            CircuitStep("Команда машиниста", "Кнопки выбирают передний или задний токоприёмник."),
            CircuitStep("Пневматическая безопасность", "Вентиль защиты 104 пропускает воздух только через закрытые блокировки дверей и штор ВВК."),
            CircuitStep("Электрические разрешения", "Реле 248 получает питание при исправных межсекционных блокировках и достаточном давлении в резервуаре ГВ."),
            CircuitStep("Клапан 245", "Контакт реле 248 включает электропневматический клапан выбранного токоприёмника."),
            CircuitStep("Подъём", "Воздух поступает в цилиндр привода, и полоз токоприёмника прижимается к контактному проводу.")
        ),
        color = Color(0xFF7B1FA2)
    ),
    CircuitScenario(
        title = "Вспомогательные",
        summary = "Обмотка собственных нужд питает однофазные потребители и фазорасщепитель, который создаёт трёхфазное питание машин.",
        nodes = listOf(
            eNode("transformer", 20f, 25f), eNode("auxWinding", 225f, 25f), eNode("phaseSplitter", 430f, 25f), eNode("threePhase", 635f, 25f),
            eNode("fans12", 20f, 175f), eNode("fans34", 225f, 175f), eNode("oilPump", 430f, 175f), eNode("motorCompressor", 635f, 175f),
            eNode("trpsh", 225f, 325f), eNode("control50", 430f, 325f), eNode("battery", 635f, 325f)
        ),
        edges = listOf(
            CircuitEdge("transformer", "auxWinding", 0), CircuitEdge("auxWinding", "phaseSplitter", 1), CircuitEdge("phaseSplitter", "threePhase", 1),
            CircuitEdge("threePhase", "fans12", 2), CircuitEdge("threePhase", "fans34", 2), CircuitEdge("threePhase", "oilPump", 2), CircuitEdge("threePhase", "motorCompressor", 2),
            CircuitEdge("auxWinding", "trpsh", 3), CircuitEdge("trpsh", "control50", 3), CircuitEdge("trpsh", "battery", 3)
        ),
        steps = listOf(
            CircuitStep("Собственные нужды", "При включённом ГВ отдельная обмотка трансформатора выдаёт напряжения для вспомогательного оборудования."),
            CircuitStep("Формирование трёх фаз", "Фазорасщепитель НБ-455А формирует систему питания асинхронных вспомогательных двигателей."),
            CircuitStep("Работа машин", "Через контакторы включаются вентиляторы, маслонасос и мотор-компрессор; каждая ветвь имеет свои защиты."),
            CircuitStep("Цепи управления", "ТРПШ-2 с выпрямителем питает сеть 50–55 В и заряжает аккумуляторную батарею.")
        ),
        color = Color(0xFF00897B)
    ),
    CircuitScenario(
        title = "Реостатный тормоз",
        summary = "ТЭД переводятся в генераторный режим, а их энергия рассеивается тормозными резисторами под управлением БУРТ.",
        nodes = listOf(
            eNode("km84", 20f, 25f), eNode("bp", 225f, 25f), eNode("brakeSwitch", 430f, 25f), eNode("ted", 635f, 25f), eNode("brakeRes", 840f, 25f),
            eNode("transformer", 20f, 175f), eNode("excitation", 225f, 175f), eNode("burt", 430f, 175f), eNode("fans34", 635f, 175f), eNode("pvu", 430f, 325f)
        ),
        edges = listOf(
            CircuitEdge("km84", "bp", 0), CircuitEdge("bp", "brakeSwitch", 0),
            CircuitEdge("transformer", "excitation", 1), CircuitEdge("burt", "excitation", 1), CircuitEdge("excitation", "ted", 1),
            CircuitEdge("brakeSwitch", "ted", 2), CircuitEdge("ted", "brakeRes", 2), CircuitEdge("fans34", "brakeRes", 3),
            CircuitEdge("pvu", "burt", 4), CircuitEdge("pvu", "brakeSwitch", 4)
        ),
        steps = listOf(
            CircuitStep("Переключение режима", "При нулевой главной рукоятке тормозная рукоятка переводит БП и переключатели 49/50 в положение торможения."),
            CircuitStep("Возбуждение", "ВУ возбуждения 60 создаёт регулируемый ток обмоток возбуждения; БУРТ управляет его величиной."),
            CircuitStep("Генераторный режим", "ТЭД превращают механическую энергию движения в электрическую и отдают её в тормозные резисторы."),
            CircuitStep("Охлаждение", "МВ3/МВ4 и УПВ направляют поток воздуха на нагревающиеся тормозные резисторы."),
            CircuitStep("Связь с пневматикой", "ПВУ1 и ПВУ2 имеют разные цепи и уставки: они контролируют давление, ограничивают недопустимое совместное действие тормозов и участвуют в замещении при срыве. Ориентировочные пороги 1,3–1,5 и 0,5 кгс/см² проверяют по схеме конкретной секции.")
        ),
        color = Color(0xFFFF6F00)
    ),
    CircuitScenario(
        title = "Защита",
        summary = "Измерительные и защитные реле обнаруживают опасный режим и разбирают силовую схему через ГВ или контакторы.",
        nodes = listOf(
            eNode("transformer", 20f, 25f), eNode("ted", 20f, 175f), eNode("sensors", 225f, 100f), eNode("protection", 430f, 100f),
            eNode("gv", 635f, 25f), eNode("switchgear", 635f, 175f), eNode("signal", 840f, 100f), eNode("control50", 430f, 275f)
        ),
        edges = listOf(
            CircuitEdge("transformer", "sensors", 0), CircuitEdge("ted", "sensors", 0),
            CircuitEdge("sensors", "protection", 1), CircuitEdge("control50", "protection", 1),
            CircuitEdge("protection", "gv", 2), CircuitEdge("protection", "switchgear", 2),
            CircuitEdge("protection", "signal", 3)
        ),
        steps = listOf(
            CircuitStep("Контроль", "Трансформаторы тока, РМТ, реле заземления и реле перегрузки наблюдают за силовыми цепями и ТЭД."),
            CircuitStep("Решение защиты", "При превышении уставки или замыкании на корпус защитные и промежуточные реле формируют команду отключения."),
            CircuitStep("Адресный разбор схемы", "В зависимости от вида защиты отключается главный выключатель, соответствующие линейные контакторы либо тормозная цепь. Одновременное отключение всех аппаратов не является универсальным правилом."),
            CircuitStep("Сигнал машинисту", "Контакты реле включают индикацию, по которой определяют сработавшую защиту перед восстановлением схемы.")
        ),
        color = Color(0xFFC62828)
    )
)

internal fun electricalScenarioTitles(): List<String> = electricalScenarios.map { it.title }

internal fun electricalScenarioStepCounts(): List<Int> = electricalScenarios.map { it.steps.size }

internal fun allElectricalComponentsHaveDetails(): Boolean = electricalInfo.values.all {
    it.purpose.isNotBlank() && it.triggeredBy.isNotBlank() && it.affects.isNotBlank() && it.links.isNotBlank()
}
