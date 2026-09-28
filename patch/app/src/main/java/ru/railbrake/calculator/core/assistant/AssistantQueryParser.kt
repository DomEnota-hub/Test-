package ru.railbrake.calculator.core.assistant

import ru.railbrake.calculator.core.TechnicalFamily
import ru.railbrake.calculator.core.TechnicalSection

object AssistantQueryParser {
    private val replacements = listOf(
        "вээл восемьдесят эс" to "вл80с",
        "вээл восемьдесят с" to "вл80с",
        "вээл 80 эс" to "вл80с",
        "вээл 80 с" to "вл80с",
        "в эл восемьдесят эс" to "вл80с",
        "в эл 80 с" to "вл80с",
        "вэл восемьдесят эс" to "вл80с",
        "вл восемьдесят эс" to "вл80с",
        "вл 80 с" to "вл80с",
        "вл 80 эс" to "вл80с",
        "вл восемьдесят с" to "вл80с",
        "восемьдесят эс" to "вл80с",
        "два э с пять ка" to "2эс5к",
        "три э с пять ка" to "3эс5к",
        "два эс пять к" to "2эс5к",
        "три эс пять к" to "3эс5к",
        "2 эс 5 к" to "2эс5к",
        "3 эс 5 к" to "3эс5к",
        "2 эс 5 ка" to "2эс5к",
        "3 эс 5 ка" to "3эс5к",
        "двух эс пять ка" to "2эс5к",
        "два эс пять ка" to "2эс5к",
        "трех эс пять ка" to "3эс5к",
        "три эс пять ка" to "3эс5к",
        "гэ вэ" to "гв",
        "гэ-вэ" to "гв",
        "гэвэ" to "гв",
        "главник" to "главный выключатель",
        "групповик" to "групповой переключатель",
        "фазник" to "фазорасщепитель",
        "машинистский кран" to "кран машиниста",
        "кран триста девяносто пять" to "км 395",
        "кран 395" to "км 395",
        "не пашет" to "не работает",
        "не фурычит" to "не работает",
        "не горит экран" to "нет индикации",
        "экран не горит" to "нет индикации",
        "вырубился" to "самопроизвольно отключился",
        "вырубило" to "самопроизвольно отключило",
        "отрубился" to "самопроизвольно отключился",
        "отвалился" to "самопроизвольно отключился",
        "залипает" to "заклинивает",
        "залип" to "заклинил",
        "сифонит" to "утечка воздуха",
        "травит" to "утечка",
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
        "пэ эм" to "пм",
        "пэ-эм" to "пм",
        "тэ цэ" to "тц",
        "тэ-цэ" to "тц",
        "вэ эр четыреста восемьдесят три" to "вр 483",
        "вэ эр 483" to "вр 483",
        "ка эм 395" to "км 395",
        "мэ ка" to "мк",
        "а ка бэ" to "акб",
        "гэ эр" to "гр",
        "гэ-эр" to "гр",
        "мотор вентилятор" to "мотор-вентилятор",
        "токо приемник" to "токоприемник",
        "пантограф" to "токоприемник"
    )
    private val replacementPatterns = replacements.map { (from, to) ->
        Regex("(?<![\\p{L}\\p{N}])${Regex.escape(from)}(?![\\p{L}\\p{N}])") to to
    }
    // Speech recognition may change the gender/case of a nearby word. Require
    // both an injury verb and an electrical cause, close to one another.
    private val electricalInjury = Regex(
        "(?:\\b(?:удар\\p{L}*|шарахнул\\p{L}*|тряхнул\\p{L}*|пораж\\p{L}*)\\b(?: +\\p{L}+){0,3} +\\b(?:ток\\p{L}*|электричеств\\p{L}*)\\b)|" +
            "(?:\\b(?:ток\\p{L}*|электричеств\\p{L}*)\\b(?: +\\p{L}+){0,3} +\\b(?:удар\\p{L}*|шарахнул\\p{L}*|тряхнул\\p{L}*|пораж\\p{L}*)\\b)"
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
        ComponentVocabulary("COMPRESSOR", listOf("компрессор", "мотор-компрессор", "мотор компрессор", "мк"), "компрессор мотор-компрессор мк"),
        ComponentVocabulary("TRANSFORMER", listOf("тяговый трансформатор", "трансформатор", "трансфарматор"), "тяговый трансформатор"),
        ComponentVocabulary("PANTOGRAPH", listOf("токоприемник"), "токоприемник пантограф"),
        ComponentVocabulary("EKG", listOf("экг", "групповой переключатель", "групповик"), "экг групповой переключатель групповик"),
        ComponentVocabulary("TRACTION_MOTOR", listOf("тяговый двигатель", "тяговые двигатели", "тэд"), "тэд тяговый электродвигатель тяговые двигатели"),
        ComponentVocabulary("RECTIFIER", listOf("выпрямительная установка", "выпрямитель", "ву"), "ву выпрямительная установка выпрямитель"),
        ComponentVocabulary("MOTOR_FAN", listOf("мотор-вентилятор", "мотор вентилятор"), "мотор-вентилятор вентилятор охлаждения"),
        ComponentVocabulary("PHASE_SPLITTER", listOf("фазорасщепитель", "расщепитель фаз", "фазник"), "фазорасщепитель расщепитель фаз фазник"),
        ComponentVocabulary("BRAKE_PIPE", listOf("тормозная магистраль", "тм"), "тм тормозная магистраль"),
        ComponentVocabulary("FEED_PIPE", listOf("питательная магистраль", "пм"), "пм питательная магистраль"),
        ComponentVocabulary("MAIN_RESERVOIR", listOf("главный резервуар", "главные резервуары", "гр"), "гр главные резервуары главный резервуар"),
        ComponentVocabulary("BRAKE_CYLINDER", listOf("тормозной цилиндр", "тормозные цилиндры", "тц"), "тц тормозные цилиндры тормозной цилиндр"),
        ComponentVocabulary("AIR_DISTRIBUTOR", listOf("воздухораспределитель", "вр 483", "вр483", "вр"), "воздухораспределитель вр 483"),
        ComponentVocabulary("DRIVER_BRAKE_VALVE", listOf("кран машиниста", "машинистский кран", "км 395", "км395"), "кран машиниста км 395"),
        ComponentVocabulary("EPK", listOf("эпк", "эпк 150", "эпк150"), "эпк 150 электропневматический клапан"),
        ComponentVocabulary("BATTERY", listOf("аккумуляторная батарея", "акб", "батарея"), "акб аккумуляторная батарея"),
        ComponentVocabulary("CONTACTOR", listOf("линейный контактор", "контактор"), "линейный контактор контактор")
    )

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
        "самопроизвольно", "сам включ", "сам выключ", "мигает", "моргает", "горит постоянно",
        "глюч", "косяч", "чуд", "выруб", "отруб", "отвал", "залип", "трав", "сифон",
        "не останавлива", "пахнет гарью", "гарь", "трещит", "дребезжит", "воет", "свистит"
    )

    fun parse(rawText: String): AssistantParsedQuery {
        val normalized = normalize(rawText)
        val family = when {
            "вл80с" in normalized || "вл80" in normalized -> TechnicalFamily.VL80S
            "ермак" in normalized || "2эс5к" in normalized || "3эс5к" in normalized -> TechnicalFamily.ERMAK
            else -> null
        }

        val componentKey = componentFromAnswer(normalized)
        val failureModes = AssistantFailureModeDetector.detect(normalized)
        val safetyTopicIds = AssistantSafetyTopics.resolve(normalized, electricalInjury.containsMatchIn(normalized))
        val safetyTopicId = safetyTopicIds.firstOrNull()

        val intent = when {
            safetyTopicId != null || hasSafetyCue(normalized) -> AssistantIntent.SAFETY
            hasAcceptanceCue(normalized) -> AssistantIntent.ACCEPTANCE
            hasSchemeCue(normalized) -> AssistantIntent.OPEN_SCHEME
            failureModes.isNotEmpty() || troubleshootCues.any(normalized::contains) -> AssistantIntent.TROUBLESHOOT
            hasProcedureCue(normalized) -> AssistantIntent.PROCEDURE
            hasDefinitionCue(normalized) -> AssistantIntent.DEFINE_TERM
            else -> AssistantIntent.FIND_TOPIC
        }

        val ambiguity = ambiguity(normalized, componentKey, intent, failureModes)

        val preferredSection = when (intent) {
            AssistantIntent.TROUBLESHOOT -> TechnicalSection.DIAGNOSTICS
            AssistantIntent.OPEN_SCHEME -> if ("пневм" in normalized) TechnicalSection.PNEUMATIC else TechnicalSection.ELECTRICAL
            AssistantIntent.ACCEPTANCE -> TechnicalSection.ACCEPTANCE
            AssistantIntent.SAFETY -> TechnicalSection.SAFETY
            AssistantIntent.PROCEDURE -> TechnicalSection.KNOWLEDGE
            AssistantIntent.DEFINE_TERM -> if (componentKey != null) TechnicalSection.EQUIPMENT else TechnicalSection.KNOWLEDGE
            AssistantIntent.FIND_TOPIC -> if (componentKey != null && "атлас" in normalized) TechnicalSection.EQUIPMENT else null
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
            failureModes = failureModes,
            ambiguity = ambiguity,
            safetyTopicId = safetyTopicId,
            safetyTopicIds = safetyTopicIds
        )
    }

    fun familyFromAnswer(rawText: String): TechnicalFamily? {
        val normalized = normalize(rawText)
        return when {
            normalized in setOf("вл", "вл80", "вл80с", "80с") ||
                "вл80с" in normalized || "вл80" in normalized -> TechnicalFamily.VL80S
            normalized in setOf("ермак", "2эс5к", "3эс5к") ||
                "ермак" in normalized || "2эс5к" in normalized || "3эс5к" in normalized -> TechnicalFamily.ERMAK
            else -> null
        }
    }

    fun componentFromAnswer(rawText: String): String? {
        val normalized = normalize(rawText)
        // Crews also say "в тормозной" with "магистрали" omitted. Limit that
        // interpretation to a trailing phrase so "тормозной цилиндр" stays distinct.
        if (Regex("(^| )в тормозной( (вл80с|вл80|ермак|2эс5к|3эс5к))?$").containsMatchIn(normalized)) {
            return "BRAKE_PIPE"
        }
        return componentVocabulary.firstOrNull { component ->
            component.aliases.any { alias -> containsComponentTerm(normalized, alias) }
        }?.key
    }

    fun componentKeysInDescription(text: String): Set<String> {
        val normalized = normalize(text)
        return componentVocabulary.asSequence()
            .filter { component -> component.aliases.any { alias -> containsComponentTerm(normalized, alias) } }
            .map(ComponentVocabulary::key)
            .toSet()
    }

    fun componentSearchText(componentKey: String): String? =
        componentVocabulary.firstOrNull { it.key == componentKey }?.enrichment

    fun normalize(text: String): String {
        var result = text
            .lowercase()
            .replace('ё', 'е')
            .replace(Regex("[^\\p{L}\\p{N}\\s_:-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        replacementPatterns.forEach { (pattern, to) ->
            result = result.replace(pattern, to)
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

        val textWords = text.split(' ').filter(String::isNotBlank)
        val termWords = term.split(' ').filter(String::isNotBlank)
        return termWords.all { pattern ->
            val stemLength = when {
                pattern.length >= 12 -> 7
                pattern.length >= 7 -> 5
                else -> pattern.length
            }
            val stem = pattern.take(stemLength)
            textWords.any { word -> word.startsWith(stem) }
        }
    }

    private fun containsComponentTerm(text: String, term: String): Boolean {
        // Short abbreviations shared with everyday language (гр, пм) need
        // railway context unless the entire request is that abbreviation.
        if (term in setOf("гр", "пм") && text != term &&
            listOf("вл80", "ермак", "2эс5к", "3эс5к", "магистрал", "давлен", "резервуар",
                "тормоз", "утеч", "трав", "сифон", "схем", "компрессор", "поезд").none(text::contains)
        ) return false
        return containsTerm(text, term)
    }

    private fun ambiguity(
        normalized: String,
        componentKey: String?,
        intent: AssistantIntent,
        failureModes: Set<AssistantFailureMode>
    ): AssistantAmbiguity? {
        val scopeCore = stripSeriesContext(normalized)
        return when {
            normalized in setOf("гв", "главный выключатель") -> AssistantAmbiguity.TOPIC_SCOPE
            normalized in setOf("схема", "электросхема", "пневмосхема") -> AssistantAmbiguity.SERIES_REQUIRED
            isGenericBrakeTest(normalized) -> AssistantAmbiguity.PROCEDURE_TYPE_REQUIRED
            normalized == "тормоза" -> AssistantAmbiguity.TOPIC_SCOPE
            intent == AssistantIntent.TROUBLESHOOT && componentKey == null &&
                scopeCore in setOf("тормоза чудят", "тормоза не работают", "тормоза неисправны") -> AssistantAmbiguity.TOPIC_SCOPE
            intent == AssistantIntent.TROUBLESHOOT && componentKey == null && (
                scopeCore in setOf("не работает", "не включается", "не выключается", "не запускается", "ошибка", "авария") ||
                    (AssistantFailureMode.PRESSURE_LEAK in failureModes &&
                        ("воздух" in normalized || "давление" in normalized))
                ) -> AssistantAmbiguity.COMPONENT_REQUIRED
            else -> null
        }
    }

    private fun stripSeriesContext(text: String): String {
        var result = text
            .replace(Regex("\\bвл80с\\b|\\bвл80\\b|\\b2эс5к\\b|\\b3эс5к\\b|ермак[а-я]*"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val prepositions = setOf("на", "у", "для", "по")
        result = result.split(' ')
            .filter { it.isNotBlank() && it !in prepositions }
            .joinToString(" ")
        return result
    }

    private fun isGenericBrakeTest(text: String): Boolean =
        "проба" in text && "тормоз" in text &&
            listOf("полная", "сокращ", "технолог").none { marker -> marker in text }

    private fun hasSafetyCue(text: String): Boolean =
        listOf(
            "охрана труда", "безопасность", "первая помощь", "опп", "переохлаж", "обморож",
            "слр", "реанимац", "без сознания", "не дышит", "кровотеч", "кровь не останавли", "подавил", "ожог", "обжег", "обжог",
            "сломал руку", "сломала руку", "сломал ногу", "сломала ногу", "сломана рука", "сломана нога",
            "отрав", "перелом", "судорог", "укус", "тепловой удар",
            "аптеч"
        ).any(text::contains)

    private fun hasAcceptanceCue(text: String): Boolean =
        listOf("приемк", "принимать локомотив", "осмотр снаружи", "начать снаружи", "начать из кабины").any(text::contains)

    private fun hasSchemeCue(text: String): Boolean =
        listOf("схем", "электросх", "пневмосх", "покажи где", "где находится").any(text::contains)

    private fun hasProcedureCue(text: String): Boolean =
        listOf(
            "проба тормоз", "минутная готовность", "порядок", "как выполня", "как проводится",
            "как проверить", "как осмотреть", "проверка", "когда нужна", "процедура"
        ).any(text::contains)

    private fun hasDefinitionCue(text: String): Boolean =
        listOf("что такое", "для чего", "зачем нужен", "зачем нужна", "назначение", "что делает", "расскажи про", "описание", "как устроен").any(text::contains)
}
