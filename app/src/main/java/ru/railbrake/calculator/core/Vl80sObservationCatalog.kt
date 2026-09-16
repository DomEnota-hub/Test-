package ru.railbrake.calculator.core

enum class ObservationKind(val title: String) {
    SIGNAL_LAMP("Сигнальная лампа"),
    INSTRUMENT("Прибор"),
    SOUND("Звук"),
    SMELL_OR_HEAT("Запах / нагрев"),
    BEHAVIOUR("Поведение оборудования")
}

data class DiagnosticObservation(
    val id: String,
    val title: String,
    val kind: ObservationKind,
    val description: String,
    val synonyms: List<String>,
    val scenarioIds: List<String>,
    val equipmentIds: List<String>,
    val confidence: InformationConfidence,
    val variantNote: String
)

data class EquipmentReference(
    val id: String,
    val title: String,
    val aliases: List<String>,
    val purpose: String,
    val connections: List<String>,
    val location: String,
    val locationHotspotId: String? = null,
    val scenarioIds: List<String>,
    val confidence: InformationConfidence,
    val variantNote: String
)

/**
 * «Что я вижу?» и единый поиск используют один офлайн-каталог. Названия ламп и
 * панелей могут различаться, поэтому конкретные обозначения сознательно имеют
 * вариантную пометку, а не выдаются за универсальные.
 */
object Vl80sObservationCatalog {
    val observations = listOf(
        observation("gv-lamp", "ГВ не включился / отключился", ObservationKind.SIGNAL_LAMP, "Команда на главный выключатель не подтверждается или ГВ отключился после включения.", listOf("главник", "выбило гв", "гв отвалился", "не держит гв"), listOf("gv-no-close", "protection-trip", "transformer-protection"), listOf("gv", "transformer")),
        observation("td-lamp", "Нет тяги группы ТД", ObservationKind.SIGNAL_LAMP, "Не подтверждается включение тяговой группы либо отсутствует её ток.", listOf("тд", "нет тока", "тяга секции", "линейный контактор"), listOf("traction-no-assemble", "traction-current-imbalance", "rectifier-group-fault"), listOf("bsa", "rectifier", "traction-motors")),
        observation("rp-lamp", "Сработала защита / РП", ObservationKind.SIGNAL_LAMP, "Появился признак защитного отключения; точное реле обязательно записывают по фактической схеме.", listOf("рп", "реле перегрузки", "защита", "выбило"), listOf("protection-trip", "transformer-protection", "rectifier-group-fault"), listOf("protection", "bsa")),
        observation("alsn-lamp", "АЛСН/ЭПК: неожиданная индикация или срабатывание", ObservationKind.SIGNAL_LAMP, "Показание локомотивной сигнализации изменилось либо ЭПК дал тормозное воздействие.", listOf("эпк", "бдительность", "коды", "локомотивный светофор"), listOf("alsn-epk", "uncommanded-braking"), listOf("alsn", "epk")),
        observation("pressure-tm-drop", "Падает давление тормозной магистрали", ObservationKind.INSTRUMENT, "Давление ТМ падает без ожидаемой команды либо не удаётся зарядить магистраль.", listOf("тм падает", "утечка тм", "магистраль", "тм не заряжается"), listOf("brake-pipe-leak", "brake-pipe-no-charge", "equalizing-reservoir-mismatch", "uncommanded-braking", "brakes-no-apply-release"), listOf("km395", "equalizing-reservoir", "brake-pipe", "epk")),
        observation("pressure-main-low", "Не растёт давление главных резервуаров", ObservationKind.INSTRUMENT, "При работе компрессоров запас воздуха не набирается или быстро теряется.", listOf("гр", "главные резервуары", "давление не растет", "компрессор"), listOf("compressor-no-start", "main-reservoir-leak", "compressor-pressure"), listOf("compressor", "main-reservoirs")),
        observation("tc-not-release", "Сохраняется давление ТЦ / не отпускает тормоз", ObservationKind.INSTRUMENT, "После штатного отпуска давление цилиндров или тормозной эффект сохраняются.", listOf("тц", "колодки", "юз", "не отпускает"), listOf("wheel-dragging", "independent-brake-no-release", "brake-cylinder-imbalance", "brakes-no-apply-release", "uncommanded-braking"), listOf("tc", "kvt254", "pressure-relay", "brake-rigging")),
        observation("current-imbalance", "Разный ток групп ТЭД", ObservationKind.INSTRUMENT, "Токи секций или групп заметно различаются при одинаковом режиме.", listOf("асимметрия тока", "амперметр", "одна группа"), listOf("traction-current-imbalance", "persistent-wheel-slip", "rectifier-group-fault"), listOf("traction-motors", "rectifier", "ekg")),
        observation("ekg-no-step", "ЭКГ не идёт / застряла на позиции", ObservationKind.BEHAVIOUR, "Позиции не набираются, не подтверждаются либо переход сопровождается защитой.", listOf("экг", "не набирает позиции", "застряла"), listOf("ekg-stuck", "traction-no-assemble", "traction-current-imbalance"), listOf("ekg", "bsa")),
        observation("pantograph", "Токоприёмник не поднимается", ObservationKind.BEHAVIOUR, "Выбранный токоприёмник не поднимается либо сразу опускается.", listOf("пантограф", "рога", "не поднимается"), listOf("pantograph-no-rise", "control-voltage-low", "compressor-pressure"), listOf("pantograph", "valve245")),
        observation("aux-not-start", "Не запускаются вспомогательные машины", ObservationKind.BEHAVIOUR, "Не запускается один или несколько приводов собственных нужд.", listOf("вспомогательные", "вентиляторы", "фр", "фазорасщепитель"), listOf("aux-machines", "aux-common-loss", "phase-splitter-no-start", "motor-fan-failure"), listOf("phase-splitter", "motor-fans")),
        observation("compressor-continuous", "Компрессор работает постоянно", ObservationKind.BEHAVIOUR, "Компрессор не отключается по верхнему давлению либо не успевает компенсировать расход.", listOf("компрессор не отключается", "часто включается", "кт-6"), listOf("compressor-long-run", "compressor-no-start", "main-reservoir-leak", "brake-pipe-leak"), listOf("compressor", "pressure-regulator")),
        observation("fan-no-flow", "Нет потока вентиляции", ObservationKind.BEHAVIOUR, "Двигатель вентилятора не запускается или вращается без подтверждённого потока.", listOf("мв", "вентилятор", "охлаждение", "слабый поток"), listOf("fan-low-airflow", "motor-fan-failure", "phase-splitter-no-start", "rheostatic-brake"), listOf("motor-fans", "phase-splitter")),
        observation("slip", "Боксование", ObservationKind.BEHAVIOUR, "Боксование повторяется или защита работает без очевидной причины.", listOf("бокс", "буксование", "песок"), listOf("persistent-wheel-slip", "sanding-failure", "traction-current-imbalance"), listOf("anti-slip", "sanders", "traction-motors")),
        observation("noise", "Ненормальный стук или вибрация", ObservationKind.SOUND, "Новый шум связан со скоростью, тягой или торможением.", listOf("стук", "вибрация", "гул", "скрежет"), listOf("mechanical-noise-heating", "wheel-dragging", "motor-fan-failure"), listOf("bogie", "traction-drive", "motor-fans")),
        observation("air-leak", "Сильное шипение воздуха", ObservationKind.SOUND, "Слышен непрерывный выход воздуха или заметна потеря давления.", listOf("шипит", "утечка воздуха", "свист"), listOf("main-reservoir-leak", "brake-pipe-leak", "compressor-pressure"), listOf("main-reservoirs", "brake-pipe", "compressor")),
        observation("smoke", "Дым, запах горелой изоляции или дуга", ObservationKind.SMELL_OR_HEAT, "Любой такой признак переводит работу в аварийный режим до выяснения причины.", listOf("дым", "горелое", "озон", "дуга", "искрит"), listOf("smoke-fire-flashover", "fire-alarm-signal", "transformer-protection", "protection-trip"), listOf("fire-system", "transformer", "vvk")),
        observation("hot-wheel", "Нагрев колеса, буксы или тормоза", ObservationKind.SMELL_OR_HEAT, "Есть запах, дым, сигнал контроля или визуальный признак перегрева ходовой части.", listOf("горячая букса", "нагрев", "колесо", "тормоз греется"), listOf("mechanical-noise-heating", "wheel-dragging"), listOf("bogie", "tc", "brake-rigging"))
    )

    val equipment = listOf(
        equipment("gv", "Главный выключатель", listOf("ГВ", "ВОВ-25", "главник"), "Коммутирует высоковольтное питание электровоза и отключается защитами.", listOf("токоприёмник", "тяговый трансформатор", "цепи удержания", "защиты"), "Высоковольтная камера; точная камера и обозначение — по схеме секции.", null, listOf("gv-no-close", "protection-trip", "transformer-protection")),
        equipment("pantograph", "Токоприёмник", listOf("пантограф", "рога"), "Снимает ток с контактного провода.", listOf("клапан 245", "защитный вентиль", "ГВ", "контактная сеть"), "На крыше секции.", null, listOf("pantograph-no-rise", "smoke-fire-flashover")),
        equipment("ekg", "ЭКГ", listOf("главный контроллер", "позиции ЭКГ"), "Ступенчато изменяет включение обмоток/цепей в тяговом режиме согласно схеме.", listOf("контроллер машиниста", "ВУ", "БСА", "сигнализация положения"), "Аппаратная зона; точное место и вариант исполнения — по плану кузова и схеме.", null, listOf("ekg-stuck", "traction-no-assemble", "traction-current-imbalance")),
        equipment("bsa", "Блок силовых аппаратов", listOf("БСА", "линейные контакторы"), "Группирует коммутацию, ослабление поля и часть защит тяговой группы.", listOf("ЭКГ", "ВУ", "ТЭД", "реле перегрузки", "БУРТ"), "Секция, шкафной блок БСА №1/№2.", "bsa1", listOf("traction-no-assemble", "traction-current-imbalance", "protection-trip")),
        equipment("rectifier", "Выпрямительная установка", listOf("ВУ", "выпрямитель"), "Преобразует питание тяговой цепи для групп ТЭД.", listOf("тяговый трансформатор", "БСА", "ТЭД", "вентиляция"), "Секция, силовая аппаратная зона; точное расположение зависит от исполнения.", null, listOf("rectifier-group-fault", "traction-current-imbalance")),
        equipment("transformer", "Тяговый трансформатор", listOf("трансформатор", "ТС"), "Понижает и распределяет напряжение для тяговых и вспомогательных цепей.", listOf("ГВ", "ЭКГ", "ВУ", "цепи собственных нужд", "защиты"), "Центральная трансформаторная зона секции.", null, listOf("transformer-protection", "gv-no-close", "smoke-fire-flashover")),
        equipment("phase-splitter", "Фазорасщепитель", listOf("ФР"), "Формирует питание вспомогательных трёхфазных цепей.", listOf("обмотка собственных нужд", "контакторы", "вентиляторы", "компрессоры"), "Секция, зона ФР на учебной схеме.", "phase-splitter", listOf("phase-splitter-no-start", "aux-common-loss", "aux-machines")),
        equipment("motor-fans", "Мотор-вентиляторы", listOf("МВ", "МВ3", "МВ4"), "Обеспечивают охлаждение закреплённых групп оборудования.", listOf("ФР", "контакторы", "воздуховоды", "ТЭД/ВУ/резисторы"), "Секция, зоны МВ3/МВ4 на учебной схеме.", "mv3", listOf("fan-low-airflow", "motor-fan-failure", "rheostatic-brake")),
        equipment("compressor", "Мотор-компрессор", listOf("МК", "КТ-6Эл"), "Создаёт сжатый воздух для тормозов и пневмоаппаратов.", listOf("АК-11Б", "ГР", "питательная магистраль", "клапаны"), "Секция, зона МК на учебной схеме.", "mk", listOf("compressor-long-run", "compressor-no-start", "main-reservoir-leak", "compressor-pressure")),
        equipment("km395", "Кран машиниста №395", listOf("КМ", "395"), "Управляет давлением ТМ через уравнительный резервуар.", listOf("УР", "ТМ", "БТ №367М", "ЭПК"), "Кабина машиниста.", null, listOf("brake-pipe-no-charge", "equalizing-reservoir-mismatch", "brake-pipe-leak", "brakes-no-apply-release", "uncommanded-braking")),
        equipment("kvt254", "Кран вспомогательного тормоза №254", listOf("КВТ", "254"), "Управляет независимым тормозом локомотива и участвует в повторительном действии по схеме.", listOf("РД №304", "ТЦ", "БТ №367М"), "Кабина машиниста.", null, listOf("independent-brake-no-apply", "independent-brake-no-release", "wheel-dragging", "brakes-no-apply-release")),
        equipment("alsn", "АЛСН и ЭПК", listOf("локомотивный светофор", "ЭПК-150"), "Принимают кодовые сигналы и обеспечивают контроль безопасности движения.", listOf("приёмные катушки", "локомотивный светофор", "ЭПК", "ТМ"), "Кабинное оборудование и пневматическая часть; зависит от установленного комплекса.", "alsn", listOf("alsn-epk", "uncommanded-braking")),
        equipment("burt", "БУРТ", listOf("блок управления реостатным торможением"), "Управляет логикой реостатного торможения и его защитами.", listOf("контроллер", "контакторы", "возбуждение ТЭД", "резисторы", "ПВУ"), "Секция, правая аппаратная зона на учебной схеме.", "burt", listOf("rheostatic-brake", "motor-fan-failure")),
        equipment("sanders", "Песочницы", listOf("форсунки", "песок"), "Подают песок в зону контакта колеса и рельса для улучшения сцепления.", listOf("клапаны", "воздух", "противобоксовочная защита"), "Под кузовом/у тележек; точное сопло зависит от направления и схемы.", null, listOf("sanding-failure", "persistent-wheel-slip")),
        equipment("bogie", "Тележка и колёсные пары", listOf("букса", "колесо", "ходовая"), "Передают тяговое/тормозное усилие и обеспечивают движение по рельсу.", listOf("ТЭД", "тяговая передача", "рычажная передача", "буксы"), "Под кузовом секции.", null, listOf("mechanical-noise-heating", "wheel-dragging", "persistent-wheel-slip")),
        equipment("protection", "Реле и цепи защит", listOf("защита", "РП", "реле перегрузки"), "Контролируют аварийные и ненормальные режимы, формируя отключение или сигнал.", listOf("ГВ", "БСА", "ТЭД", "ВУ", "трансформатор"), "Панели реле и аппаратные зоны; обозначение зависит от исполнения.", "relay-panels", listOf("protection-trip", "gv-no-close")),
        equipment("epk", "ЭПК-150", listOf("ЭПК", "электропневматический клапан"), "Создаёт тормозное воздействие при срабатывании системы безопасности.", listOf("АЛСН", "ТМ", "цепи бдительности"), "Кабинная и пневматическая часть, по фактическому комплексу безопасности.", null, listOf("alsn-epk", "uncommanded-braking")),
        equipment("brake-pipe", "Тормозная магистраль", listOf("ТМ", "магистраль"), "Передаёт команду автоматического торможения и обеспечивает зарядку тормозов состава.", listOf("КМ №395", "ЭПК", "рукава", "воздухораспределители"), "Вдоль локомотива и поезда.", null, listOf("brake-pipe-no-charge", "equalizing-reservoir-mismatch", "brake-pipe-leak", "uncommanded-braking")),
        equipment("main-reservoirs", "Главные резервуары", listOf("ГР", "резервуары"), "Хранят запас сжатого воздуха.", listOf("компрессоры", "питательная магистраль", "предохранительные клапаны"), "Под кузовом/в машинном отделении в зависимости от схемы секции.", null, listOf("main-reservoir-leak", "compressor-no-start")),
        equipment("tc", "Тормозные цилиндры", listOf("ТЦ"), "Преобразуют давление воздуха в тормозное усилие через рычажную передачу.", listOf("КВТ №254", "РД №304", "рычажная передача"), "На тележках локомотива.", null, listOf("independent-brake-no-apply", "independent-brake-no-release", "brake-cylinder-imbalance", "wheel-dragging", "brakes-no-apply-release")),
        equipment("pressure-relay", "Регулятор давления №304", listOf("РД", "304"), "Передаёт/регулирует давление в ветви независимого тормоза по схеме.", listOf("КВТ №254", "ТЦ", "питательная магистраль"), "Пневматическое оборудование секции; точное место сверять по схеме.", null, listOf("independent-brake-no-apply", "independent-brake-no-release", "brake-cylinder-imbalance", "wheel-dragging", "brakes-no-apply-release")),
        equipment("valve245", "Клапан токоприёмника №245", listOf("245", "электропневматический клапан токоприёмника"), "Управляет подачей воздуха к приводу токоприёмника в соответствии с цепью управления.", listOf("токоприёмник", "защитный вентиль", "цепи 50 В"), "Пневматическая/аппаратная зона; конкретное размещение сверять по схеме.", null, listOf("pantograph-no-rise")),
        equipment("anti-slip", "Противобоксовочная защита", listOf("защита боксования", "бокс"), "Обнаруживает признаки боксования и формирует предусмотренное схемой воздействие.", listOf("ТЭД", "датчики/реле", "песочницы", "цепи тяги"), "Релейные панели и цепи управления; зависит от исполнения.", "relay-panels", listOf("persistent-wheel-slip", "sanding-failure")),
        equipment("traction-motors", "Тяговые двигатели", listOf("ТЭД", "двигатели"), "Создают тяговое усилие и участвуют в электрическом торможении.", listOf("ВУ", "БСА", "тяговая передача", "вентиляция"), "На тележках локомотива.", null, listOf("traction-current-imbalance", "persistent-wheel-slip", "traction-no-assemble")),
        equipment("traction-drive", "Тяговая передача", listOf("редуктор", "зубчатая передача"), "Передаёт момент ТЭД к колёсной паре.", listOf("ТЭД", "колёсная пара", "подвеска двигателя"), "На тележке у ТЭД и колёсной пары.", null, listOf("mechanical-noise-heating", "persistent-wheel-slip")),
        equipment("fire-system", "Пожарная сигнализация", listOf("датчики пожара", "пожарка"), "Обнаруживает признаки пожара и формирует сигнал.", listOf("датчики", "шлейф", "вентиляция", "кабинная сигнализация"), "Датчики распределены по зонам оборудования.", null, listOf("fire-alarm-signal", "smoke-fire-flashover")),
        equipment("vvk", "Высоковольтная камера", listOf("ВВК"), "Ограждает и размещает высоковольтные аппараты и блокировки.", listOf("ГВ", "силовые контакторы", "трансформатор", "защиты"), "Аппаратные отсеки ВВК1/ВВК2 на учебной схеме.", "vvk1", listOf("gv-no-close", "transformer-protection", "smoke-fire-flashover")),
        equipment("brake-rigging", "Тормозная рычажная передача", listOf("рычажка", "колодки"), "Передаёт усилие тормозных цилиндров к колодкам.", listOf("ТЦ", "колодки", "колёсные пары"), "На тележках локомотива.", null, listOf("wheel-dragging", "mechanical-noise-heating")),
        equipment("pressure-regulator", "Регулятор давления компрессора", listOf("АК-11Б", "регулятор давления"), "Автоматически включает и отключает мотор-компрессор в заданном диапазоне.", listOf("компрессор", "контактор", "главные резервуары"), "Аппаратная/пневматическая зона по схеме конкретной секции.", null, listOf("compressor-long-run", "compressor-no-start", "compressor-pressure")),
        equipment("equalizing-reservoir", "Уравнительный резервуар", listOf("УР"), "Задаёт темп и величину изменения давления, которое кран машиниста преобразует в управление тормозной магистралью.", listOf("КМ №395", "ТМ", "манометр УР"), "Кабина и пневматическая разводка секции.", null, listOf("equalizing-reservoir-mismatch", "brakes-no-apply-release")),
        equipment("feed-line", "Питательная магистраль", listOf("ПМ", "напорная магистраль"), "Передаёт запас сжатого воздуха от главных резервуаров к пневматическим потребителям.", listOf("ГР", "компрессор", "КМ №395", "РД №304"), "Вдоль локомотива; точную разводку смотреть по пневматической схеме.", null, listOf("feed-line-leak", "main-reservoir-leak")),
        equipment("battery", "Аккумуляторная батарея", listOf("АКБ", "батарея"), "Поддерживает низковольтные цепи управления при отсутствии или переходных режимах основного источника.", listOf("цепи 50 В", "зарядный источник", "автоматы", "контакторы"), "Аккумуляторный отсек по плану конкретной секции.", null, listOf("control-voltage-low", "battery-no-charge", "battery-overcharge")),
        equipment("charging-unit", "Источник зарядки цепей управления", listOf("ТРПШ", "зарядный агрегат"), "Питает цепи управления и обеспечивает заряд аккумуляторной батареи в штатном режиме.", listOf("АКБ", "цепи 50 В", "автоматы", "контроль напряжения"), "Аппаратная зона; исполнение источника зависит от модернизации секции.", null, listOf("battery-no-charge", "battery-overcharge", "control-voltage-low")),
        equipment("relay-panel", "Релейная панель цепей управления", listOf("панель реле", "релейный шкаф"), "Размещает реле, соединения и часть защит низковольтных цепей.", listOf("цепи 50 В", "автоматы", "межсекционные соединения", "контакторы"), "Аппаратная зона по монтажной схеме исполнения.", "relay-panels", listOf("control-fuse-trip", "relay-panel-overheat", "section-control-loss")),
        equipment("reverser", "Реверсор", listOf("реверсивный переключатель"), "Задаёт направление тока в тяговых двигателях и подтверждает выбранное направление движения.", listOf("контроллер машиниста", "ТЭД", "цепи контроля", "линейные контакторы"), "Силовая аппаратная зона; точное размещение — по схеме секции.", null, listOf("reverser-no-confirm", "traction-no-assemble")),
        equipment("handbrake", "Ручной тормоз локомотива", listOf("стояночный тормоз", "ручник"), "Создаёт механическое удерживающее усилие через элементы рычажной передачи.", listOf("рычажная передача", "колодки", "колёсные пары"), "Привод и передача по конструкции конкретной секции.", null, listOf("handbrake-not-release", "wheel-dragging")),
        equipment("pressure-gauges", "Манометры пневматических систем", listOf("манометр", "приборы давления"), "Показывают состояние ГР, ТМ, УР и тормозных цилиндров в предусмотренной схеме.", listOf("ГР", "ТМ", "УР", "ТЦ", "импульсные линии"), "Пульт кабины и соединительные линии.", null, listOf("pressure-gauge-mismatch", "equalizing-reservoir-mismatch", "brake-cylinder-imbalance", "compressor-pressure", "brakes-no-apply-release")),
        equipment("fire-suppression", "Система пожаротушения", listOf("пожаротушение", "баллоны", "генераторы аэрозоля"), "Предназначена для штатного воздействия на очаг в предусмотренных конструкцией зонах.", listOf("пожарная сигнализация", "датчики", "пусковая цепь", "огнетушители"), "Размещение исполнительных элементов зависит от исполнения локомотива.", null, listOf("extinguisher-system-fault", "smoke-fire-flashover")),
        equipment("radio", "Поездная радиостанция", listOf("радиосвязь", "радиостанция"), "Обеспечивает установленную поездную и маневровую радиосвязь.", listOf("антенна", "питание", "пульт управления", "гарнитура"), "Кабина и антенно-фидерный тракт.", null, listOf("radio-communication-loss")),
        equipment("cab-lighting", "Прожектор и буферные фонари", listOf("прожектор", "буферные", "освещение"), "Обеспечивают видимость и обозначение локомотива в предусмотренных режимах.", listOf("автоматы", "переключатели", "лампы/модули", "проводка"), "Кабина и наружные световые приборы.", null, listOf("headlight-fault"))
    )

    fun search(query: String): Pair<List<DiagnosticObservation>, List<EquipmentReference>> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return observations to equipment
        fun text(values: List<String>) = values.joinToString(" ").lowercase().contains(q)
        return observations.filter { text(listOf(it.title, it.description) + it.synonyms) } to
            equipment.filter { text(listOf(it.title, it.purpose, it.location) + it.aliases + it.connections) }
    }

    fun observation(id: String): DiagnosticObservation? = observations.firstOrNull { it.id == id }
    fun equipment(id: String): EquipmentReference? = equipment.firstOrNull { it.id == id }

    private fun observation(
        id: String, title: String, kind: ObservationKind, description: String, synonyms: List<String>,
        scenarios: List<String>, equipment: List<String>
    ) = DiagnosticObservation(id, title, kind, description, synonyms, scenarios, equipment, InformationConfidence.REQUIRES_VARIANT_CHECK, "Обозначение лампы и её логика зависят от фактически установленной схемы/комплекса.")

    private fun equipment(
        id: String, title: String, aliases: List<String>, purpose: String, connections: List<String>, location: String,
        hotspot: String?, scenarios: List<String>
    ) = EquipmentReference(id, title, aliases, purpose, connections, location, hotspot, scenarios, InformationConfidence.REQUIRES_VARIANT_CHECK, "Точное расположение, нумерация и состав аппарата должны быть сверены по схеме конкретной секции.")
}
