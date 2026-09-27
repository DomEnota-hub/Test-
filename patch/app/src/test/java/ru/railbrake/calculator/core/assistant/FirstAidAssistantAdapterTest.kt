package ru.railbrake.calculator.core.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.railbrake.calculator.ui.firstAidTopics

class FirstAidAssistantAdapterTest {
    @Test
    fun frostbiteTopicIsProjectedAsFirstAidDocument() {
        val topic = firstAidTopics.first { it.id == "frostbite" }
        val document = FirstAidAssistantAdapter.adapt(topic)

        assertEquals(AssistantDocumentKind.FIRST_AID, document.kind)
        assertEquals("frostbite", document.canonicalId)
        assertEquals(AssistantTarget.FirstAid("frostbite"), document.target)
        assertTrue("обморожение" in document.searchText)
        assertTrue("первая помощь" in document.searchText)
    }
}
