package ru.railbrake.calculator.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class FirstAidTopic(
    val id: String,
    val title: String,
    val whenToUse: List<String>,
    val actions: List<String>,
    val dont: List<String> = emptyList(),
    val note: String? = null
)

private val firstAidTopics = listOf(
    FirstAidTopic(
        id = "unconscious",
        title = "Нет сознания",
        whenToUse = listOf("Человек не отвечает на обращение и осторожное прикосновение."),
        actions = listOf(
            "Убедитесь, что место безопасно для вас, пострадавшего и окружающих.",
            "Проверьте дыхание. Вызовите 112 или 103, привлеките помощника.",
            "Если дыхание сохранено, поддерживайте проходимость дыхательных путей и при возможности придайте устойчивое боковое положение.",
            "Постоянно контролируйте дыхание до передачи медикам."
        ),
        dont = listOf("Не оставляйте человека без наблюдения.", "Не давайте пить, есть или лекарства человеку без сознания.")
    ),
    FirstAidTopic(
        id = "cpr",
        title = "Не дышит / СЛР",
        whenToUse = listOf("Пострадавший без сознания и нормального самостоятельного дыхания."),
        actions = listOf(
            "Вызовите 112/103 или поручите вызов другому человеку.",
            "Уложите пострадавшего на спину на твёрдую поверхность и начните надавливания на центр грудной клетки.",
            "Для взрослого: глубина 5–6 см, частота 100–120 надавливаний в минуту.",
            "Если умеете и можете безопасно проводить искусственное дыхание: чередуйте 30 надавливаний и 2 вдоха, предпочтительно через устройство «Рот-Устройство-Рот».",
            "Продолжайте до появления признаков жизни, прибытия помощи или невозможности продолжать."
        ),
        dont = listOf("Не тратьте время на длительный поиск пульса без подготовки.", "Не прекращайте СЛР без причины, если признаки жизни не появились.")
    ),
    FirstAidTopic(
        id = "bleeding",
        title = "Кровотечение",
        whenToUse = listOf(
            "Кровотечения бывают наружными и внутренними; наружные по интенсивности могут быть слабыми или сильными. Отдельные ситуации — носовое кровотечение и травматический отрыв части конечности.",
            "Для первой помощи важнее не угадывать тип повреждённого сосуда, а быстро распознать продолжающуюся опасную кровопотерю.",
            "Признаки сильного наружного кровотечения: быстро пропитывающаяся одежда/повязка, заметное скопление крови, интенсивно текущая кровь."
        ),
        actions = listOf(
            "Наденьте медицинские перчатки, если они доступны.",
            "Сразу выполните прямое давление на рану чистой/стерильной салфеткой или тканью.",
            "Наложите давящую повязку. Если она быстро пропитывается и кровотечение не остановлено, примените кровоостанавливающий жгут на конечность.",
            "Жгут должен оставаться видимым. Обязательно зафиксируйте точное время наложения и передайте эту информацию медикам.",
            "Вызовите 112/103 при сильном кровотечении или признаках значительной кровопотери. При подозрении на внутреннее кровотечение после серьёзной травмы обеспечьте покой и срочно вызовите помощь."
        ),
        dont = listOf("Не извлекайте глубоко находящиеся инородные предметы из раны.", "Не закрывайте жгут одеждой или повязкой.")
    ),
    FirstAidTopic(
        id = "airway",
        title = "Подавился",
        whenToUse = listOf(
            "Частичная непроходимость: человек может говорить и кашлять.",
            "Полная непроходимость: не может говорить/дышать, дыхание резко затруднено, может держаться за горло."
        ),
        actions = listOf(
            "Если человек эффективно кашляет, побуждайте продолжать кашель и наблюдайте.",
            "При полной непроходимости наклоните пострадавшего вперёд и выполните до 5 ударов основанием ладони между лопатками.",
            "Если не помогло, выполните до 5 надавливаний на верхнюю часть живота внутрь и вверх. Чередуйте 5 ударов по спине и 5 надавливаний.",
            "При потере сознания начните СЛР и вызовите 112/103."
        ),
        dont = listOf("Не пытайтесь вслепую вынимать предмет пальцами изо рта.")
    ),
    FirstAidTopic(
        id = "trauma",
        title = "Травмы и переломы",
        whenToUse = listOf("Сильная боль, деформация, отёк, нарушение функции конечности, рана, возможный перелом или вывих."),
        actions = listOf(
            "Сначала остановите опасное наружное кровотечение.",
            "Придайте повреждённой части тела удобное положение и обеспечьте покой. Не исправляйте деформацию силой.",
            "Приложите холод через ткань к области повреждения.",
            "Если требуется переноска и это безопасно, иммобилизацию можно выполнить шиной или подручным жёстким материалом поверх одежды; безопасная альтернатива для конечности — фиксация к здоровой части тела.",
            "При тяжёлой травме, подозрении на травму головы/позвоночника, таза или множественные повреждения вызовите 112/103 и без необходимости не перемещайте человека."
        ),
        dont = listOf("Не вправляйте кости и суставы.", "Не удаляйте выступающие из раны костные отломки или инородные предметы.")
    ),
    FirstAidTopic(
        id = "burn",
        title = "Ожоги",
        whenToUse = listOf("Термический или химический ожог, поражение горячей поверхностью, пламенем, паром или химическим веществом."),
        actions = listOf(
            "Прекратите действие повреждающего фактора, не подвергая себя опасности.",
            "Термический ожог охлаждайте прохладной проточной водой около 20 минут, если это возможно и безопасно.",
            "При химическом поражении кожи удалите сухое вещество безопасным способом и длительно промывайте проточной водой, если для конкретного вещества это не запрещено его инструкцией.",
            "Закройте повреждённую поверхность чистой сухой повязкой. При обширном/глубоком ожоге, поражении лица, дыхательных путей, глаз или электрическом ожоге вызовите 112/103."
        ),
        dont = listOf("Не вскрывайте пузыри.", "Не отрывайте прилипшую одежду.", "Не наносите масло, жир или мази на свежий ожог.")
    ),
    FirstAidTopic(
        id = "electric",
        title = "Электротравма",
        whenToUse = listOf("Контакт с токоведущей частью, электрическая дуга, подозрение на шаговое напряжение, электрический ожог."),
        actions = listOf(
            "СНАЧАЛА обеспечьте собственную безопасность. Не прикасайтесь к пострадавшему, пока воздействие тока не прекращено безопасным способом.",
            "На железной дороге учитывайте контактную сеть, высоковольтное оборудование и возможное шаговое напряжение. При напряжении свыше 1000 В отделение пострадавшего допускается только после снятия напряжения и заземления либо подготовленным персоналом с предусмотренными средствами защиты.",
            "После безопасного прекращения воздействия тока оцените сознание и дыхание; при отсутствии нормального дыхания начните СЛР.",
            "Вызовите 112/103. Электротравма требует медицинской оценки даже при внешне удовлетворительном состоянии."
        ),
        dont = listOf("Не хватайте пострадавшего голыми руками, если источник тока не устранён.", "Не входите в зону возможного шагового напряжения без установленного безопасного порядка.")
    ),
    FirstAidTopic(
        id = "poisoning",
        title = "Отравление / химия",
        whenToUse = listOf("Пары/газы, неизвестное вещество, химическое попадание на кожу/в глаза, подозрение на отравление."),
        actions = listOf(
            "Не входите в загрязнённую зону без подходящих средств защиты. Прекратите воздействие вещества только если это безопасно.",
            "При вдыхании газа/паров переместите пострадавшего на свежий воздух, если это можно сделать без риска для спасателя.",
            "При попадании вещества на кожу/в глаза начинайте промывание большим количеством воды, если паспорт/маркировка вещества не требует иного.",
            "Вызовите 112/103 и сообщите известное название вещества, путь воздействия и время. Сохраните упаковку/этикетку для медиков."
        ),
        dont = listOf("Не вызывайте рвоту у человека без сознания.", "Не нейтрализуйте неизвестные химикаты другими реагентами наугад.")
    ),
    FirstAidTopic(
        id = "cold",
        title = "Переохлаждение / обморожение",
        whenToUse = listOf("Длительное воздействие холода, дрожь, заторможенность, холодная/бледная кожа, потеря чувствительности отдельных участков."),
        actions = listOf(
            "Переместите человека в тёплое безопасное место, снимите мокрую одежду и замените сухой, укутайте.",
            "Если человек в сознании, можно дать тёплое сладкое питьё.",
            "При наличии изотермического покрывала укройте им поверх утепляющего слоя, оставив лицо открытым.",
            "При выраженном переохлаждении, нарушении сознания или дыхания вызовите 112/103 и контролируйте дыхание."
        ),
        dont = listOf("Не растирайте обмороженные участки.", "Не согревайте их резко горячей водой, у огня или прямой грелкой.", "Не давайте алкоголь.")
    ),
    FirstAidTopic(
        id = "bites",
        title = "Укус / ужаливание",
        whenToUse = listOf("Укус или ужаливание животного/насекомого, особенно при подозрении на яд или быстро нарастающую общую реакцию."),
        actions = listOf(
            "Прекратите контакт с источником опасности, обеспечьте покой и наблюдайте за дыханием и сознанием.",
            "При подозрении на ядовитый укус, выраженном отёке, слабости, нарушении дыхания или сознания срочно вызовите 112/103.",
            "Снимите кольца, часы и другие сдавливающие предметы с повреждённой конечности до нарастания отёка; ограничьте её движения."
        ),
        dont = listOf("Не разрезайте и не прижигайте место укуса.", "Не накладывайте жгут на конечность только из-за укуса без продолжающегося сильного кровотечения.", "Не выполняйте сомнительные процедуры вместо вызова помощи.")
    ),
    FirstAidTopic(
        id = "stress",
        title = "Острая реакция на стресс",
        whenToUse = listOf("Плач, выраженный страх, апатия, возбуждение или агрессивная реакция после происшествия."),
        actions = listOf(
            "Сначала обеспечьте физическую безопасность и исключите травму/угрожающее жизни состояние.",
            "Оставайтесь рядом, говорите коротко и спокойно, сообщайте человеку, что происходит и какая помощь вызвана.",
            "По возможности оградите от толпы, лишнего шума и повторного воздействия травмирующей ситуации.",
            "Если человек опасен для себя/окружающих, резко дезориентирован или есть сомнения в состоянии здоровья, привлеките экстренные службы."
        ),
        dont = listOf("Не спорьте с человеком и не обесценивайте его реакцию.", "Не оставляйте одного человека с выраженной дезориентацией или риском самоповреждения.")
    ),
    FirstAidTopic(
        id = "seizure",
        title = "Судороги",
        whenToUse = listOf("Судорожный приступ, особенно с потерей сознания."),
        actions = listOf(
            "Поддержите падающего человека и уберите опасные предметы вокруг.",
            "Защитите голову мягким предметом, не прижимая её к земле.",
            "После окончания судорог, если сознание не восстановилось, но дыхание есть, придайте устойчивое боковое положение и контролируйте дыхание.",
            "Вызовите 112/103 при первом приступе, травме, длительном/повторном приступе, беременности, нарушении дыхания или если состояние вызывает сомнение."
        ),
        dont = listOf("Не удерживайте судороги силой.", "Не пытайтесь разжать челюсти и ничего не вставляйте в рот.")
    )
)

private val workerKitLines = listOf(
    "2 одноразовые медицинские маски и не менее 2 пар медицинских перчаток.",
    "2 устройства для искусственного дыхания «Рот-Устройство-Рот».",
    "1 кровоостанавливающий жгут для верхней/нижней конечности.",
    "Не менее 4 бинтов 5 м × 10 см и 4 бинтов 7 м × 14 см (или предусмотренные приказом фиксирующие эластичные варианты).",
    "2 упаковки стерильных салфеток не менее 16 × 13 см №10.",
    "1 рулон фиксирующего лейкопластыря не менее 2 × 500 см; 10 бактерицидных пластырей не менее 1,9 × 7,2 см; 2 пластыря не менее 4 × 10 см.",
    "2 спасательных изотермических покрывала не менее 160 × 210 см.",
    "1 ножницы для разрезания перевязочного материала и ткани.",
    "Инструкция по оказанию первой помощи, блокнот не менее A7, чёрный/синий маркер или карандаш, футляр/сумка.",
    "Лекарственные препараты в обязательный состав аптечки работника по приказу №262н не входят."
)

@Composable
fun FirstAidScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedId by rememberSaveable { mutableStateOf("unconscious") }
    val selected = firstAidTopics.firstOrNull { it.id == selectedId }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text("← На главную") }
            Text("Оказание первой помощи", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text("Быстрый офлайн-справочник для работника. Не заменяет обучение первой помощи и указания диспетчера 112/103.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Сначала не станьте вторым пострадавшим", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error)
                    Text("Оцените угрозы для себя, пострадавшего и окружающих. На железной дороге отдельно учитывайте движущийся подвижной состав, контактную сеть и высоковольтное оборудование, шаговое напряжение, пожар/дым, механизмы и химические вещества.")
                    Text("Если доступ к человеку небезопасен, сначала организуйте устранение опасности и вызовите помощь. Не входите в опасную зону только ради более быстрого начала помощи.", fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Экстренные службы", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text("112 — единый номер. 103 — скорая медицинская помощь.")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))) }) { Text("Набрать 112") }
                        OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:103"))) }) { Text("Набрать 103") }
                    }
                    Text("Сообщите место, что произошло, число пострадавших, их состояние и какую помощь уже оказывают.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            Text("Быстрый выбор", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(firstAidTopics, key = { it.id }) { topic ->
                    FilterChip(selected = selectedId == topic.id, onClick = { selectedId = topic.id }, label = { Text(topic.title) })
                }
                item {
                    FilterChip(selected = selectedId == "kit", onClick = { selectedId = "kit" }, label = { Text("Аптечка") })
                }
            }
        }

        if (selectedId == "kit") {
            item {
                FirstAidInfoCard(
                    title = "Аптечка работника по приказу Минздрава №262н",
                    lines = workerKitLines,
                    tone = MaterialTheme.colorScheme.secondaryContainer
                )
            }
            item {
                FirstAidInfoCard(
                    title = "Что можно использовать под рукой",
                    lines = listOf(
                        "Приказ №220н допускает применение подручных средств при оказании первой помощи.",
                        "Чистая ткань/одежда может временно использоваться для давления на рану; жёсткий предмет и ткань — для осторожной иммобилизации; одежда/сумка — как мягкая опора или утепление.",
                        "Подручное средство не должно создавать новую опасность, усиливать травму или заменять специальное изделие, когда оно доступно и вы умеете его применять."
                    ),
                    tone = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        } else {
            selected?.let { topic -> item { FirstAidTopicCard(topic) } }
        }

        item {
            FirstAidInfoCard(
                title = "Нормативная основа",
                lines = listOf(
                    "Приказ Минздрава России от 03.05.2024 №220н «Об утверждении Порядка оказания первой помощи», действует с 01.09.2024.",
                    "Приказ Минздрава России от 24.05.2024 №262н — требования к аптечке работника, действует с 01.09.2024 до 01.09.2030.",
                    "Для железнодорожных электротравм дополнительно учитываются действующие инструкции по охране труда ОАО «РЖД» и местный порядок безопасного снятия напряжения/заземления.",
                    "Редакция справочника проверена: 16.09.2026. При расхождении с действующим локальным документом применяется актуальный документ работодателя и указания экстренных служб."
                ),
                tone = MaterialTheme.colorScheme.surfaceVariant
            )
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun FirstAidTopicCard(topic: FirstAidTopic) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(topic.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            if (topic.whenToUse.isNotEmpty()) {
                Text("Когда", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                topic.whenToUse.forEach { Text("• $it") }
            }
            Text("Что делать", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            topic.actions.forEachIndexed { index, action -> Text("${index + 1}. $action") }
            if (topic.dont.isNotEmpty()) {
                Text("Не делать", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                topic.dont.forEach { Text("• $it") }
            }
            topic.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun FirstAidInfoCard(title: String, lines: List<String>, tone: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = tone)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            lines.forEach { Text("• $it") }
        }
    }
}
