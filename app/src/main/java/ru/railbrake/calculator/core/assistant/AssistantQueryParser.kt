package ru.railbrake.calculator.core.assistant

import ru.railbrake.calculator.core.TechnicalFamily
import ru.railbrake.calculator.core.TechnicalSection

object AssistantQueryParser {
    private val replacements = listOf(
        "вээл восемьдесят эс" to "вл80с",
        "вээл восемьдесят с" to "вл80с",
        "вээл 80 эс" to "вл80с",
        "вл 80 с" to "вл80с",
        "двух эс пять ка" to "2эс5к",
        "два эс пять ка" to "2эс5к",
        "трех эс пять ка" to "3эс5к",
        "три эс пять ка" to "3эс5к",
        "гэ вэ" to "гв",
        "гэ-вэ" to "гв",
        "гэвэ" to "гв",
        "главник" to "главный выключатель",
        "э ка гэ" to "экг",
        "э-ка-гэ" to "экг",
        "тэ дэ" to "тэд",
        "тэ-дэ" to "тэд",
        "вэ у" to "ву",
        "вэ-у" to "ву",
        "э пэ ка" to "эпк",
        "э-пэ-ка" to "эпк",
        "ка эм триста девяносто пять" to "км 395",
        "ка-эм триста девяносто пять" to "км 395",
        "тэ эм" to "тм",
        "тэ-эм" to "тм",
        "гэ эр" to "гр",
        "гэ-эр" to "гр",
        "мотор вентилятор" to "мотор-вентилятор",
        "токо приемник" to "токоприемник",
        "пантограф" to "токоприемник"
    )

    private data class ComponentVocabulary(
        val key: String,
        val aliases: List<String>,
        val enrichment: String
    )

    /**
     * Небольшой детерминированный словарь железнодорожных узлов. Он нужен не
     * для подмены данных приложения, а для сведения разговорных и ASR-форм к
     * словам, которые уже встречаются в карточках и диагностических сценариях.
     */
    private val componentVocabulary = listOf(
        ComponentVocabulary("MAIN_BREAKER", listOf("главный выключатель", "гв"), "главный выключатель гв"),
        ComponentVocabulary("COMPRESSOR", listOf("компрессор", "мк"), "компрессор мотор-компрессор мк"),
        ComponentVocabulary("TRANSFORMER", listOf("тяговый трансформатор", "трансформатор"), "тяговый трансформатор"),
        ComponentVocabulary("PANTOGRAPH", listOf("токоприемник"), "токоприемник пантограф"),
        ComponentVocabulary("EKG", listOf("экг", "групповой переключатель"), "экг групповой переключатель"),
        ComponentVocabulary("TRACTION_MOTOR", listOf("тяговый двигатель", "тяговые двигатели", "тэд"), "тэд тяговый электродвигатель тяговые двигатели"),
        ComponentVocabulary("RECTIFIER", listOf("выпрямительная установка", "выпрямитель", "ву"), "ву выпрямительная установка выпрямитель"),
        ComponentVocabulary("MOTOR_FAN", listOf("мотор-вентилятор", "мотор вентилятор"), "мотор-вентилятор вентилятор охлаждения"),
        ComponentVocabulary("PHASE_SPLITTER", listOf("фазорасщепитель", "расщепитель фаз"), "фазорасщепитель расщепитель фаз"),
        ComponentVocabulary("BRAKE_PIPE", listOf("тормозная магистраль", "тм"), "тм тормозная магистраль"),
        ComponentVocabulary("MAIN_RESERVOIR", listOf("главный резервуар", "главные резервуары", "гр"), "гр главные резервуары главный резервуар"),
        ComponentVocabulary("BRAKE_CYLINDER", listOf("тормозной цилиндр", "тормозные цилиндры", "тц"), "тц тормозные цилиндры тормозной цилиндр"),
        ComponentVocabulary("AIR_DISTRIBUTOR", listOf("воздухораспределитель", "вр 483", "вр483"), "воздухораспределитель вр 483"),
        ComponentVocabulary("DRIVER_BRAKE_VALVE", listOf("кран машиниста", "км 395", "км395"), "кран машиниста км 395"),
        ComponentVocabulary("EPK", listOf("эпк", "эпк 150", "эпк150"), "эпк 150 электропневматический клапан"),
        ComponentVocabulary("BATTERY", listOf("аккумуляторная батарея", "акб", "батарея"), "акб аккумуляторная батарея"),
        ComponentVocabulary("CONTACTOR", listOf("линейный контактор", "контактор"), "линейный контактор контактор")
    )

    /**
     * Формулировки, которые описывают отказ/аномалию, а не справочный интерес.
     * Используются как domain signal: при наличии такого признака поиск должен
     * сначала смотреть диагностику конкретного узла, а уже затем справочник.
     */
    private val troubleshootCues = listOf(
        "диагност",
        "не включ", "не выключ", "не держ", "не срабаты", "не запуска", "не старт",
        "не кач", "не набира", "не сбрасы", "не поднима", "не опуска", "не тян",
        "не тормоз", "не отпуска", "не заряжа", "не разряжа", "не горит", "не светится",
        "не работает", "не работа",
        "отпада", "отключ", "выбива", "заклин", "застрял", "застряла", "застряло",
        "ошиб", "авари", "отказ", "неисправ", "пробой", "обрыв", "короткое замыкание",
        "тяги нет", "тяга пропала", "тягу не берет", "не берет тягу",
        "давление не", "нет давления", "молчит", "воздуха не дает",
        "дым", "искрит", "искрен", "перегрев", "греется", "стучит", "шумит", "утеч", "теч",
        "самопроизвольно", "сам включ", "сам выключ", "мигает", "моргает", "горит постоянно"
    )

    fun parse(rawText: String): AssistantParsedQuery {
        val normalized = normalize(rawText)
        val family = when {
            "вл80с" in normalized || "вл80" in normalized -> TechnicalFamily.VL80S
            "ермак" in normalized || "2эс5к" in normalized || "3эс5к" in normalized -> TechnicalFamily.ERMAK
            else -> null
        }

        val componentKey = componentVocabulary.firstOrNull { component ->
            component.aliases.any { alias -> containsTerm(normalized, alias) }
        }?.key

        val ambiguity = ambiguity(normalized, componentKey)

        val intent = when {
            hasSafetyCue(normalized) -> AssistantIntent.SAFETY
            hasAcceptanceCue(normalized) -> AssistantIntent.ACCEPTANCE
            hasSchemeCue(normalized) -> AssistantIntent.OPEN_SCHEME
            troubleshootCues.any(normalized::contains) -> AssistantIntent.TROUBLESHOOT
            hasProcedureCue(normalized) -> AssistantIntent.PROCEDURE
            hasDefinitionCue(normalized) -> AssistantIntent.DEFINE_TERM
            else -> AssistantIntent.FIND_TOPIC
        }

        val preferredSection = when (intent) {
            AssistantIntent.TROUBLESHOOT -> TechnicalSection.DIAGNOSTICS
            AssistantIntent.OPEN_SCHEME -> TechnicalSection.ELECTRICAL
            AssistantIntent.ACCEPTANCE -> TechnicalSection.ACCEPTANCE
            AssistantIntent.SAFETY -> TechnicalSection.SAFETY
            AssistantIntent.PROCEDURE -> TechnicalSection.KNOWLEDGE
            AssistantIntent.DEFINE_TERM -> if (componentKey != null) TechnicalSection.EQUIPMENT else TechnicalSection.KNOWLEDGE
            AssistantIntent.FIND_TOPIC -> null
        }

        val confidence = when (intent) {
            AssistantIntent.SAFETY -> 0.96
            AssistantIntent.ACCEPTANCE -> 0.92
            AssistantIntent.OPEN_SCHEME -> if (componentKey != null || family != null) 0.92 else 0.70
            AssistantIntent.TROUBLESHOOT -> if (componentKey != null || family != null) 0.93 else 0.73
            AssistantIntent.PROCEDURE -> 0.90
            AssistantIntent.DEFINE_TERM -> 0.89
            AssistantIntent.FIND_TOPIC -> 0.66
        }

        return AssistantParsedQuery(
            rawText = rawText,
            normalizedText = normalized,
            searchText = enrichSearchText(normalized, componentKey),
            intent = intent,
            intentConfidence = confidence,
            family = family,
            preferredSection = preferredSection,
            componentKey = componentKey,
            ambiguity = ambiguity
        )
    }

    fun normalize(text: String): String {
        var result = text
            .lowercase()
            .replace('ё', 'е')
            .replace(Regex("[^\\p{L}\\p{N}\\s-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        replacements.forEach { (from, to) ->
            result = result.replace(from, to)
        }
        return result.replace(Regex("\\s+"), " ").trim()
    }

    private fun enrichSearchText(normalized: String, componentKey: String?): String = buildString {
        append(normalized)
        componentVocabulary.firstOrNull { it.key == componentKey }?.let { component ->
            append(' ').append(component.enrichment)
        }
    }.normalizeAssistantText()

    private fun containsTerm(text: String, term: String): Boolean {
        if (term.length <= 3 && term.all { it.isLetterOrDigit() || it == ' ' }) {
            return Regex("(^| )${Regex.escape(term)}( |$)").containsMatchIn(text)
        }
        if (term in text) return true

        // Для словарных названий достаточно совпадения устойчивых начал слов:
        // «главный выключатель» ↔ «главного выключателя»,
        // «аккумуляторная батарея» ↔ «аккумуляторную батарею».
        val textWords = text.split(' ').filter(String::isNotBlank)
        val termWords = term.split(' ').filter(String::isNotBlank)
        return termWords.all { pattern ->
            val stem = pattern.take(if (pattern.length >= 7) 5 else pattern.length)
            textWords.any { word -> word.startsWith(stem) }
        }
    }

    private fun ambiguity(normalized: String, componentKey: String?): AssistantAmbiguity? = when {
        normalized in setOf("гв", "главный выключатель") -> AssistantAmbiguity.TOPIC_SCOPE
        normalized in setOf("схема", "электросхема", "пневмосхема") -> AssistantAmbiguity.SERIES_REQUIRED
        normalized in setOf("не работает", "не включается", "не выключается", "не запускается", "ошибка") && componentKey == null -> AssistantAmbiguity.COMPONENT_REQUIRED
        isGenericBrakeTest(normalized) -> AssistantAmbiguity.PROCEDURE_TYPE_REQUIRED
        normalized == "тормоза" -> AssistantAmbiguity.TOPIC_SCOPE
        else -> null
    }

    private fun isGenericBrakeTest(text: String): Boolean =
        "проба" in text && "тормоз" in text &&
            listOf("полная", "сокращ", "технолог").none { marker -> marker in text }

    private fun hasSafetyCue(text: String): Boolean =
        listOf("охрана труда", "безопасность", "переохлаж", "обморож", "первая помощь").any(text::contains)

    private fun hasAcceptanceCue(text: String): Boolean =
        listOf("приемк", "принимать локомотив", "осмотр снаружи", "начать снаружи", "начать из кабины").any(text::contains)

    private fun hasSchemeCue(text: String): Boolean =
        listOf("схема", "схеме", "электросх", "пневмосх", "покажи где", "где находится").any(text::contains)

    private fun hasProcedureCue(text: String): Boolean =
        listOf(
            "проба тормоз", "минутная готовность", "порядок", "как выполня", "как проводится",
            "как проверить", "как осмотреть", "проверка", "когда нужна", "процедура"
        ).any(text::contains)

    private fun hasDefinitionCue(text: String): Boolean =
        listOf("что такое", "для чего", "зачем нужен", "зачем нужна", "назначение", "что делает", "расскажи про", "описание").any(text::contains)
}
