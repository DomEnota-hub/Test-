package ru.railbrake.calculator.core.assistant

/**
 * A compact semantic layer for the *kind* of failure described by a query.
 * It deliberately does not try to diagnose the locomotive: it only preserves
 * distinctions that bag-of-words search loses (for example, "не включается"
 * versus "не выключается" versus "сам отключился").
 */
enum class AssistantFailureMode(val label: String) {
    NO_SWITCH_ON("не включается"),
    NO_SWITCH_OFF("не выключается"),
    SPONTANEOUS_OFF("самопроизвольно отключается"),
    NO_START("не запускается"),
    ENGINE_STALL("дизель глохнет"),
    NO_STOP("не останавливается"),
    NO_RISE("не поднимается"),
    SLOW_RISE("медленный подъём"),
    SELECTION_MISMATCH("выбран другой аппарат"),
    NO_LOWER("не опускается"),
    NO_BUILD_PRESSURE("не набирает давление"),
    PRESSURE_LEAK("утечка / падение давления"),
    NO_TRACTION("нет тяги"),
    NO_BRAKE("не тормозит"),
    SPONTANEOUS_BRAKE("самопроизвольное торможение"),
    NO_RELEASE("не отпускает"),
    OVERHEAT("перегрев"),
    SPARK_OR_ARC("искрение / дуга"),
    SMOKE_OR_FIRE("дым / пожар"),
    ABNORMAL_NOISE("необычный шум"),
    JAMMED("заклинивание"),
    GENERAL_FAILURE("общая неисправность")
}

object AssistantFailureModeDetector {
    fun detect(text: String): Set<AssistantFailureMode> {
        val normalized = text.normalizeAssistantText()
        if (normalized.isBlank()) return emptySet()

        val modes = linkedSetOf<AssistantFailureMode>()

        val compressorContext = Regex("(^| )(компрессор|мотор-компрессор|мк)( |$)").containsMatchIn(normalized) &&
            !Regex("(^| )(гв|главный выключатель)( |$)").containsMatchIn(normalized)
        val brakeContext = "тормоз" in normalized &&
            listOf("тормозной сигнал", "тормозной огонь").none(normalized::contains)

        val noSwitchOn = containsAny(
            normalized,
            "не включ", "не хочет включ", "не замыка", "не принимает команд",
            "не срабатывает на включ", "не срабатыва"
        ) && !compressorContext && !(brakeContext && "не срабатыва" in normalized)
        val noSwitchOffPhrase = containsAny(
            normalized,
            "не выключ", "не отключ", "не размыка"
        )
        val noSwitchOff = noSwitchOffPhrase && !compressorContext

        if (noSwitchOn) modes += AssistantFailureMode.NO_SWITCH_ON
        if (noSwitchOff) modes += AssistantFailureMode.NO_SWITCH_OFF

        // Plain "отключился/отключается/отпадает/выбивает" is a different event
        // from "не отключается". The negative form is checked first so the
        // shared stem cannot collapse opposite failures.
        if (!noSwitchOffPhrase && containsAny(
                normalized,
                "самопроизвольно отключ", "сам выключ", "сам отключ",
                "отключился", "отключилась", "отключилось", "отключается",
                "выключился", "выключилась", "выключилось", "выключается",
                "отпада", "отпал", "отвали", "выбива", "выбил", "сбрасывает защит"
            )
        ) {
            modes += AssistantFailureMode.SPONTANEOUS_OFF
        }

        if (containsAny(normalized, "не запуска", "не старт", "не вращ", "не пуска", "молчит") ||
            (compressorContext && containsAny(normalized, "не включ", "не хочет включ"))) {
            modes += AssistantFailureMode.NO_START
        }
        if (containsAny(normalized, "глох", "заглох", "заглохн") &&
            !containsAny(normalized, "не глох", "не заглох")) {
            modes += AssistantFailureMode.ENGINE_STALL
        }
        if (containsAny(normalized, "не останавлива", "не стопорится") ||
            (compressorContext && containsAny(normalized, "не отключ", "не выключ"))) {
            modes += AssistantFailureMode.NO_STOP
        }
        if (containsAny(normalized, "не поднима")) modes += AssistantFailureMode.NO_RISE
        if (containsAny(normalized, "поднимается медленно", "медленно поднимается", "замедленный подъем", "подъем затянут")) {
            modes += AssistantFailureMode.SLOW_RISE
        }
        if (containsAny(normalized, "не соответствует выбор", "не выбранный", "не тот что выбрал", "не тот токоприемник", "выбран другой")) {
            modes += AssistantFailureMode.SELECTION_MISMATCH
        }
        if (containsAny(normalized, "не опуска")) modes += AssistantFailureMode.NO_LOWER
        if (containsAny(
                normalized,
                "не кач", "не набира давление", "не набирает давление", "давление не набира",
                "давление не набирается", "нет давления", "воздуха не дает", "воздух не дает",
                "не создает давление"
            )
        ) {
            modes += AssistantFailureMode.NO_BUILD_PRESSURE
        }
        if (Regex("давлени[ея] (?:\\S+ ){0,5}(?<!не )пада").containsMatchIn(normalized) || containsAny(
            normalized,
            "утеч", "трав", "сифон", "падает давление", "давление пада",
                "уходит воздух", "воздух уходит", "не держит давление", "давление не держит",
                "разгермет", "падение давления", "давление тм резко пада"
            )
        ) {
            modes += AssistantFailureMode.PRESSURE_LEAK
        }
        if (containsAny(
                normalized,
                "тяги нет", "нет тяги", "не тян", "тяга пропала", "пропала тяга",
                "не берет тягу", "тягу не берет", "снятие нагрузки", "теряет нагрузку"
            )
        ) {
            modes += AssistantFailureMode.NO_TRACTION
        }
        if (containsAny(normalized, "не тормоз") ||
            (brakeContext && containsAny(normalized, "не срабатыва", "не действует"))) modes += AssistantFailureMode.NO_BRAKE
        if (brakeContext && containsAny(normalized, "самопроизвольн", "сам срабатыва", "без команды")) {
            modes += AssistantFailureMode.SPONTANEOUS_BRAKE
        }
        if (containsAny(normalized, "не отпуска")) modes += AssistantFailureMode.NO_RELEASE
        if (containsAny(normalized, "перегрев", "греется", "перегрел")) modes += AssistantFailureMode.OVERHEAT
        if (containsAny(normalized, "искрит", "искрен", "дуга", "пробой", "пробил")) modes += AssistantFailureMode.SPARK_OR_ARC
        val smokeIsAffirmative = "дым" in normalized &&
            listOf("дыма нет", "без дыма", "нет дыма").none(normalized::contains)
        val fireIsAffirmative = Regex("(^| )пожар( |$|[аеуыом])").containsMatchIn(normalized) &&
            listOf("пожара нет", "нет пожара", "без пожара").none(normalized::contains)
        val burningEquipment = "горит" in normalized &&
            listOf("не горит", "лампа", "индикатор", "экран", "дисплей", "зб", "сигнал").none(normalized::contains)
        if (smokeIsAffirmative || fireIsAffirmative || burningEquipment ||
            containsAny(normalized, "горение", "запах гари", "гарь")) {
            modes += AssistantFailureMode.SMOKE_OR_FIRE
        }
        if (containsAny(normalized, "стучит", "шумит", "трещит", "дребезжит", "воет", "свистит")) {
            modes += AssistantFailureMode.ABNORMAL_NOISE
        }
        if (containsAny(normalized, "заклин", "застр", "залип", "встал на позиц", "стоит на позиц")) {
            modes += AssistantFailureMode.JAMMED
        }

        if (modes.isEmpty() && containsAny(
                normalized,
                "не работает", "не работа", "ошиб", "авари", "отказ", "неисправ", "глюч", "косяч", "чуд"
            )
        ) {
            modes += AssistantFailureMode.GENERAL_FAILURE
        }

        return modes
    }

    private fun containsAny(text: String, vararg cues: String): Boolean = cues.any(text::contains)
}
