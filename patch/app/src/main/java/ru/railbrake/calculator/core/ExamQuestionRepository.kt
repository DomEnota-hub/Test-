package ru.railbrake.calculator.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ExamQuestion(
    val id: String,
    val blockId: String,
    val blockTitle: String,
    val sourceNumber: Int,
    val question: String,
    val correctAnswer: String,
    val category: String,
    val keywords: List<String>,
    val requiresImage: Boolean,
    val diagnosticScenarioIds: List<String>,
    val equipmentIds: List<String>,
    val knowledgeTopics: List<String>,
    val sourceVersion: String
)

class ExamQuestionRepository(context: Context) {
    val questions: List<ExamQuestion> by lazy {
        val json = context.assets.open("exam_questions.json").bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        check(root.getInt("questionCount") == 329) { "Ожидалось 329 вопросов" }
        root.getJSONArray("questions").toExamQuestions().also { parsed ->
            check(parsed.size == 329) { "Фактически загружено ${parsed.size} вопросов" }
            check(parsed.map { it.id }.toSet().size == parsed.size) { "В базе есть повторяющиеся ID" }
        }
    }

    val blocks: List<String> get() = questions.map { it.blockTitle }.distinct()
    val categories: List<String> get() = questions.map { it.category }.distinct().sorted()

    fun search(query: String, block: String? = null, category: String? = null): List<ExamQuestion> {
        val tokens = normalize(query).split(' ').filter(String::isNotBlank)
        return questions.filter { item ->
            (block == null || item.blockTitle == block) &&
                (category == null || item.category == category) &&
                tokens.all { token -> normalize(item.searchText).contains(token) }
        }
    }

    /** Facts shown in ordinary thematic UI; the full source registry remains on the unlocked screen. */
    fun thematicFacts(query: String, category: String?, limit: Int = 40): List<ExamQuestion> =
        search(query = query, category = category).filterNot { it.requiresImage }.take(limit)

    private val ExamQuestion.searchText: String
        get() = listOf(question, correctAnswer, category, keywords.joinToString(" ")).joinToString(" ")

    companion object {
        internal fun normalize(value: String): String = value
            .lowercase()
            .replace('ё', 'е')
            .replace(Regex("[^а-яa-z0-9]+"), " ")
            .trim()
    }
}

private fun JSONArray.toExamQuestions(): List<ExamQuestion> = (0 until length()).map { index ->
    val item = getJSONObject(index)
    ExamQuestion(
        id = item.getString("id"),
        blockId = item.getString("blockId"),
        blockTitle = item.getString("blockTitle"),
        sourceNumber = item.getInt("sourceNumber"),
        question = item.getString("question"),
        correctAnswer = item.getString("correctAnswer"),
        category = item.getString("category"),
        keywords = item.getJSONArray("keywords").toStrings(),
        requiresImage = item.getBoolean("requiresImage"),
        diagnosticScenarioIds = item.getJSONArray("diagnosticScenarioIds").toStrings(),
        equipmentIds = item.getJSONArray("equipmentIds").toStrings(),
        knowledgeTopics = item.getJSONArray("knowledgeTopics").toStrings(),
        sourceVersion = item.getString("sourceVersion")
    )
}

private fun JSONArray.toStrings(): List<String> = (0 until length()).map(::getString)
