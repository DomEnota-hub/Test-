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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val FIRST_AID_SOURCE =
    "Источник: приказ Минздрава России №220н; учебное пособие Минздрава России «Первая помощь», 2025."

internal data class FirstAidTopic(
    val id: String,
    val title: String,
    val whenToUse: List<String>,
    val actions: List<String>,
    val dont: List<String> = emptyList(),
    val note: String? = null,
    val source: String = FIRST_AID_SOURCE
)

internal val firstAidTopics = listOf(
    FirstAidTopic(
        id = "unconscious",
        title = "Нет сознания",
        whenToUse = listOf("Человек не отвечает на обращение и осторожное прикосновение."),
        actions = listOf(
            "Убедитесь, что место безопасно для вас, пострадавшего и окружающих.",
            "Быстро осмотрите пострадавшего: при продолжающемся сильном наружном кровотечении немедленно остановите его.",
            "Откройте дыхательные пути запрокидыванием головы с подъёмом подбородка и оцените нормальное дыхание не более 10 секунд. Редкие судорожные вдохи — не нормальное дыхание.",
            "Вызовите 112 или 103, привлеките помощника.",
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
            "Быстро освободите лицо и шею только от того, что физически закрывает рот/нос или стягивает шею: снимите маску, ослабьте шарф и тесную одежду. Не задерживайте из-за этого начало помощи.",
            "Откройте дыхательные пути запрокидыванием головы с подъёмом подбородка и оцените нормальное дыхание не более 10 секунд. Редкие судорожные вдохи считайте отсутствием нормального дыхания. Удаляйте изо рта только хорошо видимое и доступное инородное тело.",
            "Если нормального дыхания нет, вызовите 112/103 или поручите вызов другому человеку.",
            "Уложите пострадавшего на спину на твёрдую поверхность и сразу начните надавливания на центр грудной клетки.",
            "Для взрослого: глубина 5–6 см, частота 100–120 надавливаний в минуту.",
            "Если умеете и можете безопасно проводить искусственное дыхание: чередуйте 30 надавливаний и 2 вдоха, предпочтительно через устройство «Рот-Устройство-Рот».",
            "Если рядом есть автоматический наружный дефибриллятор, включите его и следуйте голосовым командам, не прерывая СЛР дольше необходимого.",
            "Продолжайте до появления признаков жизни, прибытия помощи или невозможности продолжать."
        ),
        dont = listOf(
            "Не выполняйте слепое обследование полости рта пальцами и не пытайтесь достать невидимый предмет.",
            "Не тратьте время на длительный поиск пульса без подготовки.",
            "Не откладывайте СЛР ради избыточных подготовительных действий и не прекращайте её без причины, если признаки жизни не появились."
        )
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
            "Если прямое давление невозможно, опасно или неэффективно из-за инородного тела либо выступающих костных отломков, не давите на них: зафиксируйте предмет повязкой вокруг раны и при необходимости примените жгут на конечность.",
            "Если кровь остановлена прямым давлением, наложите давящую повязку. Если давление и повязка неэффективны либо конечность обширно повреждена/оторвана, примените кровоостанавливающий жгут.",
            "Жгут накладывайте только на конечность: на 5–7 см выше раны, между раной и сердцем, не на сустав. Затяните до полной остановки кровотечения.",
            "Жгут должен оставаться видимым. Обязательно зафиксируйте точное время наложения и передайте эту информацию медикам. Самостоятельно не ослабляйте и не снимайте жгут.",
            "Вызовите 112/103 при сильном кровотечении или признаках значительной кровопотери. При подозрении на внутреннее кровотечение после серьёзной травмы обеспечьте покой и срочно вызовите помощь."
        ),
        dont = listOf(
            "Не извлекайте глубоко находящиеся инородные предметы из раны.",
            "Не закрывайте жгут одеждой или повязкой.",
            "Не используйте пальцевое прижатие артерии и максимальное сгибание конечности как замену прямому давлению, повязке или жгуту."
        )
    ),
    FirstAidTopic(
        id = "nosebleed",
        title = "Кровь из носа",
        whenToUse = listOf("Наружное кровотечение из носа у человека в сознании."),
        actions = listOf(
            "Посадите человека и слегка наклоните его голову вперёд.",
            "Попросите дышать ртом и зажмите мягкую часть носа в области крыльев на 15–20 минут.",
            "Можно приложить холод к переносице через ткань.",
            "Если через 15–20 минут кровь не остановилась, кровотечение сильное или оно началось после серьёзной травмы, вызовите 112/103 и продолжайте прижимать крылья носа."
        ),
        dont = listOf("Не запрокидывайте голову назад и не укладывайте человека на спину.", "Не просите активно сморкаться.")
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
            "При полной непроходимости наклоните пострадавшего вперёд и выполните до 5 ударов основанием ладони между лопатками, после каждого проверяя результат.",
            "Если не помогло, выполните до 5 надавливаний на верхнюю часть живота внутрь и вверх. Чередуйте 5 ударов по спине и 5 надавливаний.",
            "Беременной женщине или человеку с выраженным ожирением после ударов по спине выполняйте до 5 толчков в нижнюю часть грудной клетки; на живот не надавливайте.",
            "При потере сознания осторожно уложите человека, вызовите 112/103 и начните СЛР. Перед вдохами удаляйте только появившееся и хорошо видимое инородное тело."
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
        id = "chest_abdomen",
        title = "Рана груди / живота",
        whenToUse = listOf(
            "Открытая рана груди, особенно со свистом/выходом воздуха, пенистой кровью или выраженной одышкой.",
            "Открытая рана живота, в том числе с видимыми внутренними органами."
        ),
        actions = listOf(
            "Немедленно вызовите 112/103 и контролируйте сознание и дыхание.",
            "При проникающей ране груди закройте её герметизирующей (окклюзионной) повязкой; при отсутствии штатной используйте воздухонепроницаемый материал, закрепив его с трёх сторон и оставив нижний край свободным как клапан.",
            "Сознательному пострадавшему с ранением груди помогите принять полусидячее положение с наклоном на повреждённую сторону, если так легче дышать.",
            "Рану живота закройте чистой влажной салфеткой и свободной повязкой. Выпавшие органы не вправляйте и не прижимайте.",
            "До прибытия помощи укройте пострадавшего и постоянно контролируйте состояние. Если после герметизации груди нарастает одышка и состояние ухудшается, снимите импровизированную повязку."
        ),
        dont = listOf(
            "Не извлекайте предмет из раны — зафиксируйте его повязкой вокруг.",
            "Не давайте еду и питьё при ранении живота.",
            "Не давите на выпавшие органы и не пытайтесь вернуть их внутрь."
        )
    ),
    FirstAidTopic(
        id = "burn",
        title = "Ожоги",
        whenToUse = listOf("Термический ожог: поражение горячей поверхностью, пламенем, паром или горячей жидкостью."),
        actions = listOf(
            "Прекратите действие повреждающего фактора, не подвергая себя опасности.",
            "Охлаждайте ожог прохладной водой комнатной температуры не менее 20 минут, если это возможно и безопасно.",
            "Закройте повреждённую поверхность чистой сухой повязкой. При обширном/глубоком ожоге, поражении лица, дыхательных путей, глаз или электрическом ожоге вызовите 112/103."
        ),
        dont = listOf("Не вскрывайте пузыри.", "Не отрывайте прилипшую одежду.", "Не наносите масло, жир, мази или гелевые повязки на свежий ожог.")
    ),
    FirstAidTopic(
        id = "heat",
        title = "Перегревание",
        whenToUse = listOf("Жара или работа в тяжёлой защитной одежде; слабость, головная боль, тошнота, головокружение, горячая кожа, учащённые дыхание/сердцебиение, спутанность или потеря сознания."),
        actions = listOf(
            "Переместите пострадавшего в прохладное место, расстегните или снимите лишнюю одежду.",
            "Вызовите 112/103 при нарушении сознания, судорогах, выраженном ухудшении или подозрении на тепловой удар.",
            "Сознательному человеку дайте прохладную воду небольшими порциями.",
            "Охлаждайте постепенно: прикладывайте холод через ткань к голове, шее и подмышкам.",
            "При потере сознания с сохранённым дыханием придайте устойчивое боковое положение; при отсутствии нормального дыхания начните СЛР."
        ),
        dont = listOf("Не погружайте пострадавшего в ледяную воду и не допускайте резкого переохлаждения.", "Не давайте пить человеку с нарушенным сознанием.")
    ),
    FirstAidTopic(
        id = "electric",
        title = "Электротравма",
        whenToUse = listOf("Контакт с токоведущей частью, электрическая дуга, подозрение на шаговое напряжение, электрический ожог."),
        actions = listOf(
            "СНАЧАЛА обеспечьте собственную безопасность. Не прикасайтесь к пострадавшему, пока воздействие тока не прекращено безопасным способом.",
            "На железной дороге считайте контактную сеть и высоковольтное оборудование находящимися под напряжением, пока уполномоченный персонал не подтвердил снятие напряжения и выполнение предусмотренных мер защиты. Учитывайте возможное шаговое напряжение.",
            "После безопасного прекращения воздействия тока оцените сознание и дыхание; при отсутствии нормального дыхания начните СЛР.",
            "Вызовите 112/103. Электротравма требует медицинской оценки даже при внешне удовлетворительном состоянии."
        ),
        dont = listOf("Не хватайте пострадавшего голыми руками, если источник тока не устранён.", "Не входите в зону возможного шагового напряжения без установленного безопасного порядка.")
    ),
    FirstAidTopic(
        id = "poisoning",
        title = "Отравление",
        whenToUse = listOf("Подозрение на отравление при проглатывании, вдыхании паров/газов или другом попадании вещества в организм."),
        actions = listOf(
            "Не входите в загрязнённую зону без подходящих средств защиты. Прекратите воздействие вещества только если это безопасно.",
            "При вдыхании газа/паров переместите пострадавшего на свежий воздух, если это можно сделать без риска для спасателя.",
            "Вызовите 112/103 и сообщите известное название вещества, путь воздействия и время. Сохраните упаковку/этикетку для медиков.",
            "Если вещество проглочено: у человека в сознании промывание желудка водой с вызовом рвоты допускается приказом №220н только при отравлении ядовитым, но не едким веществом. Если вещество неизвестно, есть ожоги губ/рта или сомнения — не вызывайте рвоту и выполняйте указания диспетчера.",
            "При попадании на кожу снимите загрязнённую одежду и промойте кожу водой, защищая себя от контакта."
        ),
        dont = listOf(
            "Не вызывайте рвоту у человека без сознания и при проглатывании едкого или неизвестного вещества.",
            "Не давайте еду, лекарства или молоко без указания специалиста; можно помочь принять только собственный препарат, ранее назначенный пострадавшему врачом.",
            "Не входите в опасную атмосферу без защиты."
        )
    ),
    FirstAidTopic(
        id = "chemical",
        title = "Химическое поражение",
        whenToUse = listOf("Попадание химического вещества на кожу или в глаза."),
        actions = listOf(
            "Обеспечьте безопасность, прекратите контакт и вызовите 112/103 при выраженном поражении или неизвестном веществе.",
            "Осторожно удалите загрязнённую одежду и сухое вещество, не распространяя его и защищая собственные руки.",
            "Промывайте поражённую кожу большим количеством проточной воды не менее 20 минут, если паспорт/маркировка вещества не устанавливает другой безопасный порядок.",
            "При попадании в глаза немедленно промывайте их чистой проточной водой, не допуская стекания в здоровый глаз, и организуйте медицинскую помощь.",
            "Сообщите медикам название вещества и сохраните упаковку или этикетку."
        ),
        dont = listOf("Не нейтрализуйте вещество другим реагентом наугад.", "Не трите поражённую кожу или глаза.")
    ),
    FirstAidTopic(
        id = "hypothermia",
        title = "Переохлаждение",
        whenToUse = listOf(
            "Общее охлаждение организма: сильная дрожь, холодная бледная кожа, невнятная речь, заторможенность, сонливость, спутанность сознания или замедленное дыхание.",
            "Переохлаждение может развиться не только на морозе, но и после длительного пребывания в холодной воде, мокрой одежде или на ветру."
        ),
        actions = listOf(
            "Переместите человека в тёплое безопасное место и вызовите 112/103 при подозрении на переохлаждение.",
            "Обращайтесь с пострадавшим осторожно. Снимите мокрую одежду, если это можно сделать без лишних движений, замените сухой и укутайте, включая голову, оставив лицо открытым.",
            "Согревайте постепенно: утепляющими слоями и теплом в области туловища. Постоянно контролируйте сознание и дыхание.",
            "Тёплое безалкогольное питьё допустимо только полностью бодрствующему человеку, который может нормально глотать.",
            "При отсутствии нормального дыхания начните СЛР."
        ),
        dont = listOf(
            "Не используйте горячую ванну, прямую грелку, огонь или иное резкое нагревание.",
            "Не растирайте руки и ноги и не заставляйте человека активно двигаться.",
            "Не давайте алкоголь и не давайте пить человеку с нарушенным сознанием."
        )
    ),
    FirstAidTopic(
        id = "frostbite",
        title = "Обморожение",
        whenToUse = listOf(
            "Местное холодовое повреждение: онемение или потеря чувствительности участка, побеление или изменение цвета кожи, плотная либо восковидная кожа; после согревания возможны боль, отёк и пузыри.",
            "Чаще страдают пальцы рук и ног, нос, уши и щёки. Одновременно проверьте, нет ли признаков общего переохлаждения."
        ),
        actions = listOf(
            "Уведите пострадавшего от холода, защитите повреждённый участок и организуйте медицинскую помощь; при выраженном повреждении вызовите 112/103.",
            "Снимите кольца, часы и другие сдавливающие предметы до нарастания отёка. Осторожно уберите мокрую одежду, не травмируя кожу.",
            "Закройте участок сухой свободной стерильной повязкой; пальцы по возможности разделите сухими салфетками.",
            "Наложите теплоизолирующую повязку, обездвижьте повреждённую конечность и согревайте пострадавшего изнутри тёплым питьём только при ясном сознании и нормальном глотании.",
            "По возможности не наступайте на обмороженную стопу и не нагружайте повреждённую конечность."
        ),
        dont = listOf(
            "Не растирайте и не массируйте повреждённый участок, в том числе снегом.",
            "Не согревайте у огня, батареи, прямой грелкой или горячей водой.",
            "Не вскрывайте пузыри и не допускайте повторного замерзания."
        )
    ),
    FirstAidTopic(
        id = "bites",
        title = "Ядовитый укус / ужаливание",
        whenToUse = listOf("Укус или ужаливание ядовитого животного, насекомого, паука или другого животного либо быстро нарастающая общая реакция."),
        actions = listOf(
            "Прекратите контакт с источником опасности, обеспечьте покой и наблюдайте за дыханием и сознанием.",
            "При подозрении на ядовитый укус, выраженном отёке, слабости, нарушении дыхания или сознания срочно вызовите 112/103.",
            "Снимите кольца, часы и другие сдавливающие предметы с повреждённой конечности до нарастания отёка; ограничьте её движения.",
            "Приложите холод через ткань к месту укуса. Обычный укус неядовитого животного рассматривайте как рану и обратитесь за медицинской оценкой риска инфекции/бешенства."
        ),
        dont = listOf("Не разрезайте, не прижигайте и не отсасывайте яд.", "Не накладывайте жгут на конечность только из-за укуса без продолжающегося сильного кровотечения.", "Не выполняйте сомнительные процедуры вместо вызова помощи.")
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
            "При судорожном приступе с потерей сознания вызовите 112/103 и наблюдайте до прибытия помощи; особенно срочно сообщите о травме, беременности, повторном приступе или нарушении дыхания."
        ),
        dont = listOf("Не удерживайте судороги силой.", "Не пытайтесь разжать челюсти и ничего не вставляйте в рот.")
    )
)

internal val firstAidSearchKeywords = mapOf(
    "unconscious" to listOf("обморок", "без сознания", "не отвечает", "потерял сознание"),
    "cpr" to listOf("слр", "cpr", "реанимация", "остановка сердца", "нет дыхания", "редкие вдохи"),
    "bleeding" to listOf("кровь", "порез", "рана", "кровопотеря", "жгут", "давящая повязка"),
    "nosebleed" to listOf("нос", "кровь из носа", "носовое кровотечение"),
    "airway" to listOf("удушье", "инородное тело", "подавился", "не может говорить", "еда в горле"),
    "trauma" to listOf("перелом", "вывих", "ушиб", "отек", "отёк", "боль", "шина"),
    "chest_abdomen" to listOf("рана груди", "рана живота", "проникающая рана", "выпали органы"),
    "burn" to listOf("ожог", "кипяток", "пламя", "пар", "горячая поверхность"),
    "heat" to listOf("тепловой удар", "перегрев", "жара", "солнечный удар"),
    "electric" to listOf("ток", "удар током", "электричество", "контактная сеть", "напряжение", "электроудар"),
    "poisoning" to listOf("яд", "газ", "таблетки", "отравился", "пары"),
    "chemical" to listOf("кислота", "щелочь", "химия", "в глаза", "химический ожог"),
    "hypothermia" to listOf("замерз", "замёрз", "холод", "дрожь", "переохладился"),
    "frostbite" to listOf("отморозил", "обморозил", "онемение", "пальцы", "белая кожа"),
    "bites" to listOf("змея", "укус", "ужалила", "насекомое", "клещ"),
    "stress" to listOf("паника", "страх", "плач", "шок", "истерика"),
    "seizure" to listOf("эпилепсия", "судороги", "приступ", "трясет", "трясёт")
)

private val firstAidSearchSeparators = Regex("[^\\p{L}\\p{N}]+")

internal fun firstAidSearchTokens(query: String): List<String> = query
    .lowercase()
    .replace('ё', 'е')
    .split(firstAidSearchSeparators)
    .filter(String::isNotBlank)

internal fun firstAidTopicMatches(topic: FirstAidTopic, query: String): Boolean {
    val tokens = firstAidSearchTokens(query)
    if (tokens.isEmpty()) return true
    val searchable = buildList {
        add(topic.title)
        addAll(topic.whenToUse)
        addAll(topic.actions)
        addAll(topic.dont)
        topic.note?.let(::add)
        addAll(firstAidSearchKeywords[topic.id].orEmpty())
    }.joinToString(" ").lowercase().replace('ё', 'е')
    return tokens.all(searchable::contains)
}

internal fun firstAidTopicSearchScore(topic: FirstAidTopic, query: String): Int {
    val tokens = firstAidSearchTokens(query)
    if (tokens.isEmpty()) return 0
    val title = topic.title.lowercase().replace('ё', 'е')
    val keywords = firstAidSearchKeywords[topic.id].orEmpty().joinToString(" ").lowercase().replace('ё', 'е')
    val whenToUse = topic.whenToUse.joinToString(" ").lowercase().replace('ё', 'е')
    val actions = topic.actions.joinToString(" ").lowercase().replace('ё', 'е')
    val dont = topic.dont.joinToString(" ").lowercase().replace('ё', 'е')
    return tokens.fold(0) { score, token ->
        score + when {
            token in title -> 6
            token in keywords -> 5
            token in whenToUse -> 3
            token in actions -> 2
            token in dont -> 1
            else -> 0
        }
    }
}

internal fun firstAidKitMatches(query: String): Boolean {
    val tokens = firstAidSearchTokens(query)
    if (tokens.isEmpty()) return true
    val searchable = (listOf(
        "аптечка", "бинт", "жгут", "перчатки", "маска", "салфетки", "пластырь",
        "ножницы", "покрывало", "средства первой помощи"
    ) + workerKitLines).joinToString(" ").lowercase().replace('ё', 'е')
    return tokens.all(searchable::contains)
}

internal val workerKitLines = listOf(
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
fun FirstAidScreen(onBack: () -> Unit, initialTopicId: String? = null) {
    val context = LocalContext.current
    var selectedId by rememberSaveable(initialTopicId) {
        mutableStateOf(
            initialTopicId?.takeIf { candidate ->
                candidate == "kit" || firstAidTopics.any { it.id == candidate }
            } ?: "unconscious"
        )
    }
    var query by rememberSaveable { mutableStateOf("") }
    val visibleTopics = firstAidTopics
        .filter { firstAidTopicMatches(it, query) }
        .let { topics ->
            if (query.isBlank()) topics else topics.sortedByDescending { firstAidTopicSearchScore(it, query) }
        }
    val kitVisible = firstAidKitMatches(query)
    val visibleIds = visibleTopics.mapTo(mutableSetOf()) { it.id }
    val hasResults = visibleTopics.isNotEmpty() || kitVisible

    LaunchedEffect(query, visibleIds, kitVisible) {
        if (selectedId !in visibleIds && !(selectedId == "kit" && kitVisible)) {
            selectedId = visibleTopics.firstOrNull()?.id ?: if (kitVisible) "kit" else ""
        }
    }

    val selected = visibleTopics.firstOrNull { it.id == selectedId }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text("← На главную") }
            RailSectionHeader(
                "Оказание первой помощи",
                "Быстрый справочник работника • экстренные действия • аптечка"
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(17.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
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
            FirstAidInfoCard(
                title = "Общий порядок действий",
                lines = listOf(
                    "Обеспечьте безопасность и прекратите действие опасного фактора.",
                    "Быстро найдите продолжающееся наружное кровотечение и немедленно остановите его.",
                    "Проверьте сознание. Если ответа нет — откройте дыхательные пути и оцените нормальное дыхание не более 10 секунд.",
                    "Нет нормального дыхания или есть только редкие судорожные вдохи — вызовите 112/103 и начните СЛР. Дыхание есть, сознания нет — устойчивое боковое положение и вызов помощи.",
                    "При сохранённом сознании подробно осмотрите и опросите пострадавшего, выберите нужную карточку, затем контролируйте состояние до передачи специалистам."
                ),
                tone = MaterialTheme.colorScheme.primaryContainer
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(17.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Экстренные службы", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text("112 — единый номер. 103 — скорая медицинская помощь.")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) { Text("Набрать 112") }
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
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Найти по симптому или действию") },
                placeholder = { Text("Например: кровь, ток, замёрз") },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Text("×", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }
        item {
            Text(
                if (query.isBlank()) "Все ситуации" else "Найдено: ${visibleTopics.size + if (kitVisible) 1 else 0}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (hasResults) item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visibleTopics, key = { it.id }) { topic ->
                    FilterChip(selected = selectedId == topic.id, onClick = { selectedId = topic.id }, label = { Text(topic.title) })
                }
                if (kitVisible) item {
                    FilterChip(selected = selectedId == "kit", onClick = { selectedId = "kit" }, label = { Text("Аптечка") })
                }
            }
        }

        if (!hasResults) {
            item {
                FirstAidInfoCard(
                    title = "Ничего не найдено",
                    lines = listOf("Попробуйте название состояния, симптом или действие: «обморок», «кровь», «ожог», «ток», «замёрз», «судороги»."),
                    tone = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        } else if (selectedId == "kit" && kitVisible) {
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
                    "Учебное пособие Минздрава России «Первая помощь» для преподавателей, 2025: алгоритмы, числовые параметры и противопоказанные действия.",
                    "Для железнодорожных электротравм дополнительно учитываются действующие инструкции по охране труда ОАО «РЖД» и местный порядок безопасного снятия напряжения/заземления.",
                    "Редакция базы знаний проверена: 23.09.2026. При расхождении с действующим локальным документом применяется актуальный документ работодателя и указания экстренных служб."
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
        shape = RoundedCornerShape(17.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(topic.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            if (topic.whenToUse.isNotEmpty()) {
                Text("Когда", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                topic.whenToUse.forEach { Text("• $it") }
            }
            Text("Что делать", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            topic.actions.forEachIndexed { index, action -> Text("${index + 1}. $action", fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal) }
            if (topic.dont.isNotEmpty()) {
                Text("Не делать", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                topic.dont.forEach { Text("• $it") }
            }
            topic.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(topic.source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FirstAidInfoCard(title: String, lines: List<String>, tone: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, tone.copy(alpha = 0.65f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            lines.forEach { Text("• $it") }
        }
    }
}
