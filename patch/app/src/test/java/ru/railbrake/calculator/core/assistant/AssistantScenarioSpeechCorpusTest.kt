package ru.railbrake.calculator.core.assistant

import java.io.File
import java.util.zip.GZIPInputStream
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.railbrake.calculator.core.DiagnosticRepository
import ru.railbrake.calculator.core.parseErmakDiagnostics

/** Independently phrased symptom queries grounded in specific real cards. */
class AssistantScenarioSpeechCorpusTest {
    private data class Probe(val id: String, val spoken: String)

    @Test
    fun everyRealScenarioIsStillReachableThroughAUserShapedEngineRequest() {
        val vl = DiagnosticRepository.scenarios.map(Vl80DiagnosticAssistantAdapter::adapt)
        val ermak = parseErmakDiagnostics(JSONObject(GZIPInputStream(asset().inputStream())
            .bufferedReader(Charsets.UTF_8).use { it.readText() }))
            .map(ErmakDiagnosticAssistantAdapter::adapt)
        val engine = AssistantEngine(InMemoryAssistantIndex(vl + ermak))
        val failures = mutableListOf<String>()
        (vl.map { "ВЛ80С" to it } + ermak.map { "Ермак" to it }).forEach { (family, document) ->
            val result = engine.query("$family неисправность ${document.title}")
            val ids = (result as? AssistantEngineResult.Matches)?.hits?.map { it.document.canonicalId }.orEmpty()
            if (document.canonicalId !in ids) failures += "$family ${document.canonicalId}: $ids"
        }
        assertTrue("User-shaped full diagnostic corpus misses ${failures.size}:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun spokenSymptomsRecoverTheirScenarioWithinTheVisibleThreeResults() {
        val vl = DiagnosticRepository.scenarios.map(Vl80DiagnosticAssistantAdapter::adapt)
        val ermak = parseErmakDiagnostics(JSONObject(GZIPInputStream(asset().inputStream())
            .bufferedReader(Charsets.UTF_8).use { it.readText() }))
            .map(ErmakDiagnosticAssistantAdapter::adapt)
        val available = (vl + ermak).map(AssistantDocument::canonicalId).toSet()
        val engine = AssistantEngine(InMemoryAssistantIndex(vl + ermak))
        val probes = listOf(
            Probe("pantograph-no-rise", "ВЛ80С пантограф совсем не поднимается"),
            Probe("pantograph-slow-rise", "ВЛ80С пантограф поднимается медленно и опускается"),
            Probe("pantograph-contact-arcing", "ВЛ80С сильно искрит полоз токоприемника"),
            Probe("pantograph-wrong-selection", "ВЛ80С поднимается другой токоприемник не тот что выбрал"),
            Probe("gv-no-open", "ВЛ80С главник не хочет выключаться"),
            Probe("gv-air-loss", "ВЛ80С у ГВ воздух в резервуаре уходит"),
            Probe("ekg-stuck", "ВЛ80С ЭКГ застрял между позициями"),
            Probe("ekg-position-mismatch", "ВЛ80С позиция ЭКГ на указателе другая"),
            Probe("aux-machines", "ВЛ80С фазник не запускается"),
            Probe("compressor-pressure", "ВЛ80С компрессор не качает главные резервуары"),
            Probe("compressor-long-run", "ВЛ80С компрессор работает без остановки"),
            Probe("compressor-overheat", "ВЛ80С компрессор греется и шумит"),
            Probe("battery-no-charge", "ВЛ80С аккумулятор не заряжается"),
            Probe("battery-no-voltage", "ВЛ80С на батарее нет напряжения управления"),
            Probe("feed-line-leak", "ВЛ80С травит питательная магистраль"),
            Probe("brake-pipe-no-charge", "ВЛ80С тормозная магистраль не заряжается"),
            Probe("independent-brake-no-apply", "ВЛ80С вспомогательный тормоз не срабатывает"),
            Probe("independent-brake-no-release", "ВЛ80С вспомогательный тормоз не отпускает"),
            Probe("wheel-bearing-heat", "ВЛ80С букса сильно нагрелась"),
            Probe("wheel-flat-impact", "ВЛ80С колесо стучит из-за ползуна"),
            Probe("radio-communication-loss", "ВЛ80С пропала поездная радиосвязь"),
            Probe("wiper-fault", "ВЛ80С дворники не работают"),
            Probe("extinguisher-system-fault", "ВЛ80С система пожаротушения не готова"),
            Probe("machine-room-smoke", "ВЛ80С в машинном отделении пахнет гарью и дым"),
            Probe("external-object-impact", "ВЛ80С под локомотивом был удар посторонним предметом"),

            Probe("ER-DIAG-002", "Ермак один токоприемник не поднимается"),
            Probe("ER-DIAG-003", "Ермак оба главника не включаются"),
            Probe("ER-DIAG-004", "Ермак главный выключатель одной секции не включается"),
            Probe("ER-DIAG-005", "Ермак после включения главник одной секции отпадает"),
            Probe("ER-DIAG-017", "Ермак экран МСУД погас а тяга есть"),
            Probe("ER-DIAG-021", "Ермак все компрессоры не запускаются"),
            Probe("ER-DIAG-022", "Ермак компрессор только одной секции молчит"),
            Probe("ER-DIAG-030", "Ермак вспышка на крыше и пропало напряжение сети"),
            Probe("ER-DIAG-037", "Ермак загорелась ЗБ и МСУД пишет ОБ"),
            Probe("ER-DIAG-040", "Ермак на МСУД си ди дэ тэ резкий бросок тока"),
            Probe("ER-DIAG-044", "Ермак МСУД перезапускается на нейтральной вставке"),
            Probe("ER-DIAG-049", "Ермак тяга пропала сразу на всех секциях"),
            Probe("ER-DIAG-050", "Ермак тяга пропала только на одной секции"),
            Probe("ER-DIAG-057", "Ермак электрический тормоз не собирается в рекуперации"),
            Probe("ER-DIAG-060", "Ермак рекуперация отключается когда падает давление в ТМ"),
            Probe("ER-DIAG-066", "Ермак температура трансформатора растет"),
            Probe("ER-DIAG-071", "Ермак вспомогательный компрессор не создает давление"),
            Probe("ER-DIAG-073", "Ермак в питательной магистрали падает давление"),
            Probe("ER-DIAG-079", "Ермак межсекционная ПМ травит воздух"),
            Probe("ER-DIAG-081", "Ермак тормоз локомотива не отпускает"),
            Probe("ER-DIAG-082", "Ермак на одной тележке нет давления в тормозных цилиндрах"),
            Probe("ER-DIAG-084", "Ермак тормоз сам срабатывает без команды"),
            Probe("ER-DIAG-086", "Ермак автоматический тормоз не срабатывает"),
            Probe("ER-DIAG-090", "Ермак уходит воздух из тормозной магистрали поезда"),
            Probe("ER-DIAG-093", "Ермак на КЛУБ У не горит экран"),
            Probe("ER-DIAG-103", "Ермак песок не поступает под колеса"),
            Probe("ER-DIAG-105", "Ермак горячая букса на одной колесной паре"),
            Probe("ER-DIAG-108", "Ермак на колесе ползун и периодически стучит"),
            Probe("ER-DIAG-111", "Ермак нет поездной радиосвязи"),
            Probe("ER-DIAG-114", "Ермак дворники и омыватель не работают"),
            Probe("ER-DIAG-127", "Ермак пожарная сигнализация показывает неисправность но дыма нет"),
            Probe("ER-DIAG-128", "Ермак стационарное пожаротушение не готово"),
            Probe("ER-DIAG-136", "Ермак секции развивают разную тягу")
        )
        assertTrue(probes.size >= 55)
        val failures = mutableListOf<String>()
        probes.forEach { (id, spoken) ->
            if (id !in available) {
                failures += "$id is absent from the real catalog"
                return@forEach
            }
            when (val result = engine.query(spoken)) {
                is AssistantEngineResult.Matches -> if (result.hits.none { it.document.canonicalId == id }) {
                    failures += "$spoken -> ${result.hits.joinToString { "${it.document.canonicalId}:${it.score}" }}; expected $id"
                }
                else -> failures += "$spoken -> $result; expected $id"
            }
        }
        assertTrue("Scenario speech misses ${failures.size}/${probes.size}:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    private fun asset() = listOf(
        File("src/main/assets/technical/ermak_diagnostics.json.gz"),
        File("app/src/main/assets/technical/ermak_diagnostics.json.gz"),
        File("../app/src/main/assets/technical/ermak_diagnostics.json.gz")
    ).firstOrNull(File::isFile) ?: error("Ermak diagnostic asset missing")
}
