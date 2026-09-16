package ru.railbrake.calculator.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExamQuestionAssetTest {
    private val root by lazy {
        JSONObject(File("src/main/assets/exam_questions.json").readText())
    }

    @Test
    fun assetContainsAllReviewedQuestions() {
        val questions = root.getJSONArray("questions")
        assertEquals(329, root.getInt("questionCount"))
        assertEquals(329, questions.length())
        val ids = (0 until questions.length()).map { questions.getJSONObject(it).getString("id") }
        assertEquals(329, ids.toSet().size)
    }

    @Test
    fun blockCountsMatchSource() {
        val questions = root.getJSONArray("questions")
        val counts = (0 until questions.length())
            .map { questions.getJSONObject(it).getString("blockId") }
            .groupingBy { it }
            .eachCount()
        assertEquals(mapOf("test-1" to 164, "test-2-1" to 29, "test-2-2" to 72, "test-2-3" to 64), counts)
    }

    @Test
    fun everyQuestionHasSearchAndDistributionMetadata() {
        val questions = root.getJSONArray("questions")
        for (index in 0 until questions.length()) {
            val item = questions.getJSONObject(index)
            assertTrue(item.getString("question").isNotBlank())
            assertTrue(item.getString("correctAnswer").isNotBlank())
            assertTrue(item.getString("category").isNotBlank())
            assertTrue(item.getJSONArray("knowledgeTopics").length() > 0)
            assertFalse(item.getString("sourceVersion").isBlank())
        }
    }

    @Test
    fun searchNormalizationHandlesRussianYoAndPunctuation() {
        assertEquals("колесная пара", ExamQuestionRepository.normalize("Колёсная, пара!"))
    }
}
