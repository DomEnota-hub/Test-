package ru.railbrake.calculator.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeRepositoryTest {
    @Test
    fun searchFindsArticleByEquipmentTagIgnoringCase() {
        val results = KnowledgeRepository.search("бса", "Все")

        assertTrue(results.any { it.id == "vl80-layout" })
        assertTrue(results.any { it.id == "vl80-detail-bsa1" })
    }

    @Test
    fun categoryFilterDoesNotLeakArticlesFromOtherSections() {
        val results = KnowledgeRepository.search("", "Тормоза")

        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.category == "Тормоза" })
    }

    @Test
    fun diagramHotspotsUseValidNormalizedBounds() {
        assertTrue(KnowledgeRepository.vl80LayoutHotspots.size >= 15)
        KnowledgeRepository.vl80LayoutHotspots.forEach { hotspot ->
            assertTrue(hotspot.left in 0f..1f)
            assertTrue(hotspot.top in 0f..1f)
            assertTrue(hotspot.right in 0f..1f)
            assertTrue(hotspot.bottom in 0f..1f)
            assertTrue(hotspot.left < hotspot.right)
            assertTrue(hotspot.top < hotspot.bottom)
        }
    }

    @Test
    fun serviceBrakeRouteHasStartAndCompleteLearningChain() {
        val article = KnowledgeRepository.articles.first { it.id == "vl80-service-brake-route" }
        val route = KnowledgeRepository.vl80ServiceBrakeRoute

        assertTrue(article.hasAirRoute)
        assertTrue(route.start.contains("главные резервуары", ignoreCase = true))
        assertEquals(7, route.steps.size)
        assertTrue(route.steps.any { it.title.contains("№483") })
        assertTrue(route.steps.last().title.contains("№304"))
    }

    @Test
    fun everyDiagramHotspotHasAnOfflineDetailArticle() {
        KnowledgeRepository.vl80LayoutHotspots.forEach { hotspot ->
            val article = KnowledgeRepository.articleById("vl80-detail-${hotspot.id}")
            assertTrue(article != null)
            assertTrue(article!!.body.size >= 6)
        }
    }

    @Test
    fun alsn_and_adjacent_layout_zones_do_not_overlap() {
        val alsn = KnowledgeRepository.vl80LayoutHotspots.first { it.id == "alsn" }
        val neighbours = KnowledgeRepository.vl80LayoutHotspots.filter { it.id in setOf("cab-equipment", "phase-splitter", "relay-panels") }
        neighbours.forEach { other ->
            val overlaps = alsn.left < other.right && alsn.right > other.left &&
                alsn.top < other.bottom && alsn.bottom > other.top
            assertFalse("АЛСН перекрывает ${other.id}", overlaps)
        }
    }

    @Test
    fun fireArticleContainsVerifiedOperationalDistancesAndSequence() {
        val article = KnowledgeRepository.articleById("vl80-fire-safety")!!
        val text = article.body.joinToString(" ")
        assertTrue(text.contains("один длинный и два коротких"))
        assertTrue(text.contains("2 м"))
        assertTrue(text.contains("8 м"))
        assertTrue(text.contains("50 м"))
        assertTrue(article.source.note.orEmpty().contains("18.02.2025"))
    }

    @Test
    fun pneumaticTrainerCoversFourBaseModes() {
        val scenarios = KnowledgeRepository.pneumaticScenarios

        assertEquals(4, scenarios.size)
        assertTrue(scenarios.all { it.steps.isNotEmpty() })
        assertTrue(scenarios.any { it.mode == PneumaticMode.SERVICE_BRAKE && it.steps.any { step -> step.title.contains("№483") } })
        assertTrue(scenarios.any { it.mode == PneumaticMode.AUXILIARY_BRAKE && it.steps.any { step -> step.title.contains("№254") } })
    }
}
