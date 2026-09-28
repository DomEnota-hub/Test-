package ru.railbrake.calculator.core.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.railbrake.calculator.core.TechnicalFamily
import ru.railbrake.calculator.core.TechnicalSection

/**
 * Human-language regression corpus for the offline assistant.
 *
 * Unlike AssistantDiagnosticCorpusTest, which audits every real diagnostic card
 * by its own title/symptom, this suite intentionally uses wording a person may
 * actually type or dictate: slang, abbreviations, ASR-like spellings, typos,
 * fragments and ambiguous requests.
 */
class AssistantHumanQueryCorpusTest {

    private data class FaultPhrase(
        val text: String,
        val component: String,
        val mode: AssistantFailureMode
    )

    private data class ParserCase(
        val query: String,
        val intent: AssistantIntent,
        val family: TechnicalFamily? = null,
        val component: String? = null,
        val modes: Set<AssistantFailureMode> = emptySet(),
        val ambiguity: AssistantAmbiguity? = null,
        val section: TechnicalSection? = null
    )

    @Test
    fun humanFaultPhrasesKeepComponentSeriesAndFailureMeaning() {
        val cases = listOf(
            ParserCase("ВЛ80С ГВ не включается", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "MAIN_BREAKER", setOf(AssistantFailureMode.NO_SWITCH_ON)),
            ParserCase("на вээл восемьдесят эс гэ вэ не включается", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "MAIN_BREAKER", setOf(AssistantFailureMode.NO_SWITCH_ON)),
            ParserCase("ВЛ80С главник не хочет включаться", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "MAIN_BREAKER", setOf(AssistantFailureMode.NO_SWITCH_ON)),
            ParserCase("Ермак ГВ отключился", AssistantIntent.TROUBLESHOOT, TechnicalFamily.ERMAK, "MAIN_BREAKER", setOf(AssistantFailureMode.SPONTANEOUS_OFF)),
            ParserCase("2эс5к главник отвалился", AssistantIntent.TROUBLESHOOT, TechnicalFamily.ERMAK, "MAIN_BREAKER", setOf(AssistantFailureMode.SPONTANEOUS_OFF)),
            ParserCase("три эс пять ка гэ вэ вырубило", AssistantIntent.TROUBLESHOOT, TechnicalFamily.ERMAK, "MAIN_BREAKER", setOf(AssistantFailureMode.SPONTANEOUS_OFF)),
            ParserCase("3ЭС5К ГВ не отключается", AssistantIntent.TROUBLESHOOT, TechnicalFamily.ERMAK, "MAIN_BREAKER", setOf(AssistantFailureMode.NO_SWITCH_OFF)),
            ParserCase("ВЛ80С главник выбило", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "MAIN_BREAKER", setOf(AssistantFailureMode.SPONTANEOUS_OFF)),

            ParserCase("ВЛ80С МК не запускается", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "COMPRESSOR", setOf(AssistantFailureMode.NO_START)),
            ParserCase("ВЛ80С мотор компрессор молчит", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "COMPRESSOR", setOf(AssistantFailureMode.NO_START)),
            ParserCase("ВЛ80С компрессор не останавливается", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "COMPRESSOR", setOf(AssistantFailureMode.NO_STOP)),
            ParserCase("ВЛ80С компрессор воздух не качает", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "COMPRESSOR", setOf(AssistantFailureMode.NO_BUILD_PRESSURE)),
            ParserCase("ВЛ80С компрессор не набирает давление", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "COMPRESSOR", setOf(AssistantFailureMode.NO_BUILD_PRESSURE)),
            ParserCase("ВЛ80С компрессор сифонит", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "COMPRESSOR", setOf(AssistantFailureMode.PRESSURE_LEAK)),
            ParserCase("ВЛ80С компрессор травит воздух", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "COMPRESSOR", setOf(AssistantFailureMode.PRESSURE_LEAK)),

            ParserCase("ВЛ80С групповик залип", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "EKG", setOf(AssistantFailureMode.JAMMED)),
            ParserCase("ВЛ80С ЭКГ застрял на позиции", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "EKG", setOf(AssistantFailureMode.JAMMED)),
            ParserCase("ВЛ80С э ка гэ встал на позиции", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "EKG", setOf(AssistantFailureMode.JAMMED)),
            ParserCase("ВЛ80С фазник молчит", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "PHASE_SPLITTER", setOf(AssistantFailureMode.NO_START)),

            ParserCase("ВЛ80С пантограф не поднимается", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "PANTOGRAPH", setOf(AssistantFailureMode.NO_RISE)),
            ParserCase("ВЛ80С токоприёмник не опускается", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "PANTOGRAPH", setOf(AssistantFailureMode.NO_LOWER)),
            ParserCase("ВЛ80С ТЭД искрит", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "TRACTION_MOTOR", setOf(AssistantFailureMode.SPARK_OR_ARC)),
            ParserCase("ВЛ80С тэ дэ дымит", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "TRACTION_MOTOR", setOf(AssistantFailureMode.SMOKE_OR_FIRE)),
            ParserCase("ВЛ80С тяговый двигатель греется", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "TRACTION_MOTOR", setOf(AssistantFailureMode.OVERHEAT)),
            ParserCase("ВЛ80С ТЭД воет", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "TRACTION_MOTOR", setOf(AssistantFailureMode.ABNORMAL_NOISE)),

            ParserCase("ВЛ80С ВУ греется", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "RECTIFIER", setOf(AssistantFailureMode.OVERHEAT)),
            ParserCase("ВЛ80С вэ у пахнет гарью", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "RECTIFIER", setOf(AssistantFailureMode.SMOKE_OR_FIRE)),
            ParserCase("ВЛ80С ВУ пробило", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "RECTIFIER", setOf(AssistantFailureMode.SPARK_OR_ARC)),

            ParserCase("ВЛ80С ТМ давление падает", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "BRAKE_PIPE", setOf(AssistantFailureMode.PRESSURE_LEAK)),
            ParserCase("ВЛ80С в тормозной магистрали нет давления", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "BRAKE_PIPE", setOf(AssistantFailureMode.NO_BUILD_PRESSURE)),
            ParserCase("ВЛ80С кран 395 травит", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "DRIVER_BRAKE_VALVE", setOf(AssistantFailureMode.PRESSURE_LEAK)),
            ParserCase("ВЛ80С КМ 395 не держит давление", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "DRIVER_BRAKE_VALVE", setOf(AssistantFailureMode.PRESSURE_LEAK)),
            ParserCase("ВЛ80С ВР 483 травит", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "AIR_DISTRIBUTOR", setOf(AssistantFailureMode.PRESSURE_LEAK)),
            ParserCase("ВЛ80С ЭПК не срабатывает", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "EPK", setOf(AssistantFailureMode.NO_SWITCH_ON)),
            ParserCase("ВЛ80С АКБ не заряжается", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "BATTERY"),
            ParserCase("ВЛ80С линейный контактор не включается", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "CONTACTOR", setOf(AssistantFailureMode.NO_SWITCH_ON)),

            ParserCase("Ермак тяги нет", AssistantIntent.TROUBLESHOOT, TechnicalFamily.ERMAK, modes = setOf(AssistantFailureMode.NO_TRACTION)),
            ParserCase("3ЭС5К не берет тягу", AssistantIntent.TROUBLESHOOT, TechnicalFamily.ERMAK, modes = setOf(AssistantFailureMode.NO_TRACTION)),
            ParserCase("ВЛ80С тормоза не отпускают", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, modes = setOf(AssistantFailureMode.NO_RELEASE)),
            ParserCase("ВЛ80С не тормозит", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, modes = setOf(AssistantFailureMode.NO_BRAKE)),

            // Common misspellings should still resolve by technical stems.
            ParserCase("ВЛ80С компресор не работает", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "COMPRESSOR", setOf(AssistantFailureMode.GENERAL_FAILURE)),
            ParserCase("ВЛ80С фазорасщипитель не работает", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "PHASE_SPLITTER", setOf(AssistantFailureMode.GENERAL_FAILURE)),
            ParserCase("ВЛ80С токоприемнек не поднимается", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "PANTOGRAPH", setOf(AssistantFailureMode.NO_RISE)),
            ParserCase("ВЛ80С трансфарматор греется", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "TRANSFORMER", setOf(AssistantFailureMode.OVERHEAT))
        )

        assertParserCases(cases)
    }

    @Test
    fun humanNavigationSafetyAcceptanceAndProcedurePhrasesRouteCorrectly() {
        val cases = listOf(
            ParserCase("покажи схему ГВ на ВЛ80С", AssistantIntent.OPEN_SCHEME, TechnicalFamily.VL80S, "MAIN_BREAKER", section = TechnicalSection.ELECTRICAL),
            ParserCase("где ГВ на схеме 2эс5к", AssistantIntent.OPEN_SCHEME, TechnicalFamily.ERMAK, "MAIN_BREAKER", section = TechnicalSection.ELECTRICAL),
            ParserCase("электросхема Ермака", AssistantIntent.OPEN_SCHEME, TechnicalFamily.ERMAK, section = TechnicalSection.ELECTRICAL),
            ParserCase("пневмосхема ВЛ80С тормозная магистраль", AssistantIntent.OPEN_SCHEME, TechnicalFamily.VL80S, "BRAKE_PIPE", section = TechnicalSection.PNEUMATIC),

            ParserCase("что такое компрессор", AssistantIntent.DEFINE_TERM, component = "COMPRESSOR", section = TechnicalSection.EQUIPMENT),
            ParserCase("для чего нужен главный выключатель", AssistantIntent.DEFINE_TERM, component = "MAIN_BREAKER", section = TechnicalSection.EQUIPMENT),
            ParserCase("назначение фазорасщепителя", AssistantIntent.DEFINE_TERM, component = "PHASE_SPLITTER", section = TechnicalSection.EQUIPMENT),

            ParserCase("как проверить аккумуляторную батарею", AssistantIntent.PROCEDURE, component = "BATTERY", section = TechnicalSection.KNOWLEDGE),
            ParserCase("порядок полной пробы тормозов", AssistantIntent.PROCEDURE, section = TechnicalSection.KNOWLEDGE),
            ParserCase("сокращенная проба тормозов", AssistantIntent.PROCEDURE, section = TechnicalSection.KNOWLEDGE),
            ParserCase("технологическая проба тормозов", AssistantIntent.PROCEDURE, section = TechnicalSection.KNOWLEDGE),

            ParserCase("приемка ВЛ80С снаружи", AssistantIntent.ACCEPTANCE, TechnicalFamily.VL80S, section = TechnicalSection.ACCEPTANCE),
            ParserCase("как принимать локомотив", AssistantIntent.ACCEPTANCE, section = TechnicalSection.ACCEPTANCE),
            ParserCase("начать осмотр снаружи", AssistantIntent.ACCEPTANCE, section = TechnicalSection.ACCEPTANCE),
            ParserCase("начать из кабины", AssistantIntent.ACCEPTANCE, section = TechnicalSection.ACCEPTANCE),

            ParserCase("человека ударило током", AssistantIntent.SAFETY, section = TechnicalSection.SAFETY),
            ParserCase("человек ударила током", AssistantIntent.SAFETY, section = TechnicalSection.SAFETY),
            ParserCase("человека ударил ток", AssistantIntent.SAFETY, section = TechnicalSection.SAFETY),
            ParserCase("током ударило машиниста", AssistantIntent.SAFETY, section = TechnicalSection.SAFETY),
            ParserCase("помощника машиниста шарахнула электричеством", AssistantIntent.SAFETY, section = TechnicalSection.SAFETY),
            ParserCase("поражение электрическим током", AssistantIntent.SAFETY, section = TechnicalSection.SAFETY),
            ParserCase("электротравма", AssistantIntent.SAFETY, section = TechnicalSection.SAFETY),
            ParserCase("электроудар", AssistantIntent.SAFETY, section = TechnicalSection.SAFETY),
            ParserCase("рабочего тряхнуло током", AssistantIntent.SAFETY, section = TechnicalSection.SAFETY),
            ParserCase("удар током человека", AssistantIntent.SAFETY, section = TechnicalSection.SAFETY),
            ParserCase("человека электричеством ударило", AssistantIntent.SAFETY, section = TechnicalSection.SAFETY),
            ParserCase("шарахнуло током что делать", AssistantIntent.SAFETY, section = TechnicalSection.SAFETY),
            ParserCase("человек без сознания", AssistantIntent.SAFETY, section = TechnicalSection.SAFETY),
            ParserCase("человек не дышит", AssistantIntent.SAFETY, section = TechnicalSection.SAFETY),
            ParserCase("сильное кровотечение", AssistantIntent.SAFETY, section = TechnicalSection.SAFETY),
            ParserCase("кровь не останавливается", AssistantIntent.SAFETY, section = TechnicalSection.SAFETY),
            ParserCase("подавился человек", AssistantIntent.SAFETY, section = TechnicalSection.SAFETY),
            ParserCase("обжег руку", AssistantIntent.SAFETY, section = TechnicalSection.SAFETY),
            ParserCase("сломал руку", AssistantIntent.SAFETY, section = TechnicalSection.SAFETY),
            ParserCase("отравился", AssistantIntent.SAFETY, section = TechnicalSection.SAFETY),
            ParserCase("судороги", AssistantIntent.SAFETY, section = TechnicalSection.SAFETY),
            ParserCase("укусила собака", AssistantIntent.SAFETY, section = TechnicalSection.SAFETY),
            ParserCase("тепловой удар", AssistantIntent.SAFETY, section = TechnicalSection.SAFETY),
            ParserCase("сильно замерз", AssistantIntent.SAFETY, section = TechnicalSection.SAFETY),
            ParserCase("обморозил пальцы", AssistantIntent.SAFETY, section = TechnicalSection.SAFETY),
            ParserCase("что должно быть в аптечке", AssistantIntent.SAFETY, section = TechnicalSection.SAFETY)
        )

        assertParserCases(cases)
    }

    @Test
    fun ambiguousHumanFragmentsAskForMissingContext() {
        val cases = listOf(
            ParserCase("ГВ", AssistantIntent.FIND_TOPIC, component = "MAIN_BREAKER", ambiguity = AssistantAmbiguity.TOPIC_SCOPE),
            ParserCase("схема", AssistantIntent.OPEN_SCHEME, ambiguity = AssistantAmbiguity.SERIES_REQUIRED),
            ParserCase("не работает", AssistantIntent.TROUBLESHOOT, ambiguity = AssistantAmbiguity.COMPONENT_REQUIRED),
            ParserCase("ошибка", AssistantIntent.TROUBLESHOOT, ambiguity = AssistantAmbiguity.COMPONENT_REQUIRED),
            ParserCase("проба тормозов", AssistantIntent.PROCEDURE, ambiguity = AssistantAmbiguity.PROCEDURE_TYPE_REQUIRED),
            ParserCase("тормоза", AssistantIntent.FIND_TOPIC, ambiguity = AssistantAmbiguity.TOPIC_SCOPE),
            ParserCase("тормоза чудят", AssistantIntent.TROUBLESHOOT, ambiguity = AssistantAmbiguity.TOPIC_SCOPE),
            ParserCase("воздух уходит", AssistantIntent.TROUBLESHOOT, modes = setOf(AssistantFailureMode.PRESSURE_LEAK), ambiguity = AssistantAmbiguity.COMPONENT_REQUIRED),
            ParserCase("не работает на Ермаке", AssistantIntent.TROUBLESHOOT, TechnicalFamily.ERMAK, ambiguity = AssistantAmbiguity.COMPONENT_REQUIRED)
        )

        assertParserCases(cases)
        // A real named subject need not be in the small component dictionary.
        assertNull(AssistantQueryParser.parse("Ермак дворники не работают").ambiguity)
        assertNull(AssistantQueryParser.parse("ВЛ80С пожаротушение не готово").ambiguity)
    }

    @Test
    fun numericAliasesDoNotHijackUnrelatedNumbers() {
        val unrelated = listOf("395 рублей", "подожди 395 секунд", "страница 483", "483 человека", "150 человек", "100 гр сахара", "пм это после полудня")
        unrelated.forEach { query ->
            val parsed = AssistantQueryParser.parse(query)
            assertFalse("Numeric alias hijacked unrelated query: $query -> ${parsed.componentKey}", parsed.componentKey == "DRIVER_BRAKE_VALVE" || parsed.componentKey == "AIR_DISTRIBUTOR")
        }
        assertNull(AssistantQueryParser.parse("100 гр сахара").componentKey)
        assertNull(AssistantQueryParser.parse("пм это после полудня").componentKey)
        assertEquals(AssistantIntent.SAFETY, AssistantQueryParser.parse("человек отравится газом").intent)
    }

    @Test
    fun spokenRailwayAbbreviationsAndBrakeCircuitNamesStayDistinct() {
        val cases = listOf(
            ParserCase("в эл восемьдесят эс гэ вэ не включается", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "MAIN_BREAKER", setOf(AssistantFailureMode.NO_SWITCH_ON)),
            ParserCase("вэл восемьдесят эс главный выключатель не отключается", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "MAIN_BREAKER", setOf(AssistantFailureMode.NO_SWITCH_OFF)),
            ParserCase("вл 80 эс тэ эм давление падает", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "BRAKE_PIPE", setOf(AssistantFailureMode.PRESSURE_LEAK)),
            ParserCase("вээл 80 с ка эм 395 травит", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "DRIVER_BRAKE_VALVE", setOf(AssistantFailureMode.PRESSURE_LEAK)),
            ParserCase("вл восемьдесят с вэ эр 483 травит", AssistantIntent.TROUBLESHOOT, TechnicalFamily.VL80S, "AIR_DISTRIBUTOR", setOf(AssistantFailureMode.PRESSURE_LEAK)),
            ParserCase("два э с пять ка утечка пэ эм", AssistantIntent.TROUBLESHOOT, TechnicalFamily.ERMAK, "FEED_PIPE", setOf(AssistantFailureMode.PRESSURE_LEAK)),
            ParserCase("три э с пять ка тэ цэ нет давления", AssistantIntent.TROUBLESHOOT, TechnicalFamily.ERMAK, "BRAKE_CYLINDER", setOf(AssistantFailureMode.NO_BUILD_PRESSURE)),
            ParserCase("2 эс 5 ка мэ ка не запускается", AssistantIntent.TROUBLESHOOT, TechnicalFamily.ERMAK, "COMPRESSOR", setOf(AssistantFailureMode.NO_START)),
            ParserCase("3 эс 5 к а ка бэ не заряжается", AssistantIntent.TROUBLESHOOT, TechnicalFamily.ERMAK, "BATTERY"),
            ParserCase("тэ эм давление падает", AssistantIntent.TROUBLESHOOT, component = "BRAKE_PIPE", modes = setOf(AssistantFailureMode.PRESSURE_LEAK)),
            ParserCase("пэ эм давление падает", AssistantIntent.TROUBLESHOOT, component = "FEED_PIPE", modes = setOf(AssistantFailureMode.PRESSURE_LEAK)),
            ParserCase("тэ цэ не отпускают", AssistantIntent.TROUBLESHOOT, component = "BRAKE_CYLINDER", modes = setOf(AssistantFailureMode.NO_RELEASE)),
            ParserCase("питательная магистраль травит", AssistantIntent.TROUBLESHOOT, component = "FEED_PIPE", modes = setOf(AssistantFailureMode.PRESSURE_LEAK)),
            ParserCase("утечка в тормозной", AssistantIntent.TROUBLESHOOT, component = "BRAKE_PIPE", modes = setOf(AssistantFailureMode.PRESSURE_LEAK)),
            ParserCase("утечка в тормозной Ермак", AssistantIntent.TROUBLESHOOT, TechnicalFamily.ERMAK, "BRAKE_PIPE", setOf(AssistantFailureMode.PRESSURE_LEAK)),
            ParserCase("давление в тормозной магистрали падает", AssistantIntent.TROUBLESHOOT, component = "BRAKE_PIPE", modes = setOf(AssistantFailureMode.PRESSURE_LEAK)),
            ParserCase("тормозной цилиндр не отпускает", AssistantIntent.TROUBLESHOOT, component = "BRAKE_CYLINDER", modes = setOf(AssistantFailureMode.NO_RELEASE)),
            ParserCase("ермак компрессор не включается", AssistantIntent.TROUBLESHOOT, TechnicalFamily.ERMAK, "COMPRESSOR", setOf(AssistantFailureMode.NO_START)),
            ParserCase("ермак компрессор не отключается", AssistantIntent.TROUBLESHOOT, TechnicalFamily.ERMAK, "COMPRESSOR", setOf(AssistantFailureMode.NO_STOP)),
            ParserCase("ермак гв не отключается", AssistantIntent.TROUBLESHOOT, TechnicalFamily.ERMAK, "MAIN_BREAKER", setOf(AssistantFailureMode.NO_SWITCH_OFF)),
            ParserCase("вл компрессор не набирает давление", AssistantIntent.TROUBLESHOOT, component = "COMPRESSOR", modes = setOf(AssistantFailureMode.NO_BUILD_PRESSURE)),
            ParserCase("неисправность тормозной магистрали", AssistantIntent.TROUBLESHOOT, component = "BRAKE_PIPE", modes = setOf(AssistantFailureMode.GENERAL_FAILURE)),
            ParserCase("покажи пневмосхему питательной магистрали", AssistantIntent.OPEN_SCHEME, component = "FEED_PIPE", section = TechnicalSection.PNEUMATIC),
            ParserCase("как устроен кран машиниста", AssistantIntent.DEFINE_TERM, component = "DRIVER_BRAKE_VALVE", section = TechnicalSection.EQUIPMENT)
        )
        assertParserCases(cases)

        val negatives = listOf(
            "пэ эм давление падает" to "BRAKE_PIPE",
            "тэ эм давление падает" to "FEED_PIPE",
            "тэ цэ нет давления" to "BRAKE_PIPE",
            "тормозной цилиндр не отпускает" to "BRAKE_PIPE",
            "отравился человек" to "FEED_PIPE"
        )
        negatives.forEach { (query, wrongComponent) ->
            assertFalse("$query was mistaken for $wrongComponent", AssistantQueryParser.parse(query).componentKey == wrongComponent)
        }
        assertEquals(24, cases.size)
    }

    @Test
    fun compressorSwitchWordsDoNotCollapseIntoBreakerStates() {
        val compressorOn = AssistantQueryParser.parse("компрессор не включается").failureModes
        val compressorOff = AssistantQueryParser.parse("компрессор не отключается").failureModes
        val breakerOff = AssistantQueryParser.parse("гв не отключается").failureModes
        assertEquals(setOf(AssistantFailureMode.NO_START), compressorOn)
        assertEquals(setOf(AssistantFailureMode.NO_STOP), compressorOff)
        assertEquals(setOf(AssistantFailureMode.NO_SWITCH_OFF), breakerOff)
        assertFalse(AssistantFailureMode.PRESSURE_LEAK in AssistantQueryParser.parse("давление в ТМ не падает").failureModes)
    }

    @Test
    fun oppositeFailureStatesRemainDistinctAcrossConversationalVariants() {
        val groups = mapOf(
            AssistantFailureMode.NO_SWITCH_ON to listOf("гв не включается", "главник не хочет включаться", "гэ вэ не срабатывает на включение"),
            AssistantFailureMode.NO_SWITCH_OFF to listOf("гв не выключается", "главник не отключается", "гэ вэ не размыкается"),
            AssistantFailureMode.SPONTANEOUS_OFF to listOf("гв отключился", "главник отпал", "гв вырубило", "главник выбило")
        )

        groups.forEach { (expected, queries) ->
            queries.forEach { query ->
                val modes = AssistantQueryParser.parse(query).failureModes
                assertTrue("$query did not preserve $expected; got $modes", expected in modes)
                groups.keys.filter { it != expected }.forEach { opposite ->
                    assertFalse("$query also collapsed into opposite $opposite; got $modes", opposite in modes)
                }
            }
        }
    }

    @Test
    fun expandedSeriesAndFaultSpeechCorpusRemainsStable() {
        val series = listOf(
            "ВЛ80С" to TechnicalFamily.VL80S,
            "вл80" to TechnicalFamily.VL80S,
            "на ВЛ80С" to TechnicalFamily.VL80S,
            "вээл восемьдесят эс" to TechnicalFamily.VL80S,
            "вл 80 с" to TechnicalFamily.VL80S,
            "восемьдесят эс" to TechnicalFamily.VL80S,
            "Ермак" to TechnicalFamily.ERMAK,
            "на Ермаке" to TechnicalFamily.ERMAK,
            "2ЭС5К" to TechnicalFamily.ERMAK,
            "3ЭС5К" to TechnicalFamily.ERMAK,
            "два эс пять ка" to TechnicalFamily.ERMAK,
            "три эс пять ка" to TechnicalFamily.ERMAK
        )
        val faults = listOf(
            FaultPhrase("ГВ не включается", "MAIN_BREAKER", AssistantFailureMode.NO_SWITCH_ON),
            FaultPhrase("главник не выключается", "MAIN_BREAKER", AssistantFailureMode.NO_SWITCH_OFF),
            FaultPhrase("ГВ выключился", "MAIN_BREAKER", AssistantFailureMode.SPONTANEOUS_OFF),
            FaultPhrase("компрессор не запускается", "COMPRESSOR", AssistantFailureMode.NO_START),
            FaultPhrase("компрессор не останавливается", "COMPRESSOR", AssistantFailureMode.NO_STOP),
            FaultPhrase("компрессор не набирает давление", "COMPRESSOR", AssistantFailureMode.NO_BUILD_PRESSURE),
            FaultPhrase("токоприемник не поднимается", "PANTOGRAPH", AssistantFailureMode.NO_RISE),
            FaultPhrase("токоприемник не опускается", "PANTOGRAPH", AssistantFailureMode.NO_LOWER),
            FaultPhrase("ЭКГ застрял", "EKG", AssistantFailureMode.JAMMED),
            FaultPhrase("ТЭД искрит", "TRACTION_MOTOR", AssistantFailureMode.SPARK_OR_ARC),
            FaultPhrase("ВУ греется", "RECTIFIER", AssistantFailureMode.OVERHEAT),
            FaultPhrase("КМ 395 не держит давление", "DRIVER_BRAKE_VALVE", AssistantFailureMode.PRESSURE_LEAK)
        )

        val cases = series.flatMap { (seriesText, family) ->
            faults.map { fault ->
                ParserCase(
                    query = "$seriesText ${fault.text}",
                    intent = AssistantIntent.TROUBLESHOOT,
                    family = family,
                    component = fault.component,
                    modes = setOf(fault.mode),
                    section = TechnicalSection.DIAGNOSTICS
                )
            }
        }

        // 94 hand-written cases above + 144 systematic speech/series variants.
        assertEquals(144, cases.size)
        assertTrue(94 + cases.size in 200..300)
        assertParserCases(cases)
    }

    private fun assertParserCases(cases: List<ParserCase>) {
        val misses = mutableListOf<String>()
        cases.forEach { case ->
            val actual = AssistantQueryParser.parse(case.query)
            val problems = mutableListOf<String>()
            if (actual.intent != case.intent) problems += "intent=${actual.intent} expected=${case.intent}"
            if (case.family != null && actual.family != case.family) problems += "family=${actual.family} expected=${case.family}"
            if (case.component != null && actual.componentKey != case.component) problems += "component=${actual.componentKey} expected=${case.component}"
            if (case.modes.isNotEmpty() && !actual.failureModes.containsAll(case.modes)) problems += "modes=${actual.failureModes} expected+${case.modes}"
            if (case.ambiguity != null && actual.ambiguity != case.ambiguity) problems += "ambiguity=${actual.ambiguity} expected=${case.ambiguity}"
            if (case.section != null && actual.preferredSection != case.section) problems += "section=${actual.preferredSection} expected=${case.section}"
            if (problems.isNotEmpty()) misses += "${case.query} :: ${problems.joinToString()}"
        }

        assertTrue(
            buildString {
                append("Human-query corpus misses: ").append(misses.size).append('/').append(cases.size)
                misses.take(50).forEach { append("\n - ").append(it) }
                if (misses.size > 50) append("\n ... and ").append(misses.size - 50).append(" more")
            },
            misses.isEmpty()
        )
    }
}
