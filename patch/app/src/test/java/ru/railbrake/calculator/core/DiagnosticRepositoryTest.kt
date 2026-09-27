package ru.railbrake.calculator.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticRepositoryTest {
    @Test
    fun sourceSpecificRoutes_stopOnUnconfirmedOrDangerousEvidence() {
        val roof = DiagnosticRepository.scenario("substation-protection-roof")!!
        assertEquals(null, DiagnosticRepository.nextQuestion(roof, "sr-contact", DiagnosticResponse.YES))
        assertEquals("sr-section", DiagnosticRepository.nextQuestion(roof, "sr-contact", DiagnosticResponse.NO)?.key)
        assertEquals("sr-section", DiagnosticRepository.nextQuestion(roof, "sr-contact", DiagnosticResponse.UNKNOWN)?.key)

        val rectifier = DiagnosticRepository.scenario("rectifier-differential-trip")!!
        assertEquals("rd-damage", DiagnosticRepository.nextQuestion(rectifier, "rd-combined", DiagnosticResponse.YES)?.key)
        assertEquals("rd-damage", DiagnosticRepository.nextQuestion(rectifier, "rd-combined", DiagnosticResponse.UNKNOWN)?.key)

        val drive = DiagnosticRepository.scenario("ekg-drive-disconnected")!!
        assertEquals("ed-other", DiagnosticRepository.nextQuestion(drive, "ed-motion", DiagnosticResponse.YES)?.key)
        assertEquals("ed-other", DiagnosticRepository.nextQuestion(drive, "ed-motion", DiagnosticResponse.UNKNOWN)?.key)

        val braking = DiagnosticRepository.scenario("rheostatic-rpt-trip")!!
        assertEquals(null, DiagnosticRepository.nextQuestion(braking, "rpt-effect", DiagnosticResponse.NO))
        assertEquals("rpt-damage", DiagnosticRepository.nextQuestion(braking, "rpt-effect", DiagnosticResponse.YES)?.key)
        assertTrue(DiagnosticRepository.scenario("battery-no-voltage")!!.questions.first().key == "bv-source")

        val overload = DiagnosticRepository.scenario("traction-overload-relay-trip")!!
        assertEquals(null, DiagnosticRepository.nextQuestion(overload, "rp-physical", DiagnosticResponse.YES))
        assertEquals("rp-repeat", DiagnosticRepository.nextQuestion(overload, "rp-physical", DiagnosticResponse.NO)?.key)

        val ground = DiagnosticRepository.scenario("traction-ground-relay-trip")!!
        assertEquals(null, DiagnosticRepository.nextQuestion(ground, "rz-physical", DiagnosticResponse.YES))

        val aux113 = DiagnosticRepository.scenario("aux-relay-113-trip")!!
        assertEquals(null, DiagnosticRepository.nextQuestion(aux113, "r113-indicator", DiagnosticResponse.NO))
    }

    @Test
    fun operationalRoutesUseSourceSpecificImmediateActions() {
        val radio = DiagnosticRepository.scenario("radio-communication-loss")!!
        assertTrue(radio.immediateActions.any { it.contains("ДНЦ") && it.contains("ДСП") })
        assertTrue(radio.immediateActions.any { it.contains("ближайшей станции") })

        val coupler = DiagnosticRepository.scenario("ext-coupler-damage")!!
        assertTrue(coupler.immediateActions.any { it.contains("закреп") })
        assertTrue(coupler.immediateActions.any { it.contains("целостность ТМ") })

        val impact = DiagnosticRepository.scenario("external-object-impact")!!
        assertTrue(impact.immediateActions.any { it.contains("ТМ") && it.contains("ГР") })
        assertTrue(impact.immediateActions.any { it.contains("не возобновлять") })

        val wheel = DiagnosticRepository.scenario("wheel-flat-impact")!!
        assertTrue(wheel.immediateActions.any { it.contains("измерить") })
        assertTrue(wheel.immediateActions.any { it.contains("действующей норме") })
    }

    @Test
    fun scenarios_haveUniqueIdsAndCompleteSafetyContent() {
        val scenarios = DiagnosticRepository.scenarios

        assertTrue(scenarios.size >= 92)
        assertEquals(scenarios.size, scenarios.map { it.id }.distinct().size)
        scenarios.forEach { scenario ->
            assertTrue(scenario.title.isNotBlank())
            assertFalse(scenario.immediateActions.isEmpty())
            assertFalse(scenario.dangerSigns.isEmpty())
            assertFalse(scenario.questions.isEmpty())
            assertFalse(scenario.probableCauses.isEmpty())
            assertFalse(scenario.checks.isEmpty())
            assertFalse(scenario.prohibited.isEmpty())
            assertFalse(scenario.stopConditions.isEmpty())
            assertFalse(scenario.reportFields.isEmpty())
            assertTrue(scenario.sourceNote.isNotBlank())
            assertTrue(scenario.applicability.isNotBlank())
            assertEquals(scenario.questions.size, scenario.questions.map { it.key }.distinct().size)
            scenario.checks.forEach { check ->
                assertTrue(check.action.isNotBlank())
                assertTrue(check.expected.isNotBlank())
                assertTrue(check.ifAbnormal.isNotBlank())
            }
        }
    }

    @Test
    fun expandedScenarios_haveExplanationsFeedbackAndValidLinks() {
        val expanded = DiagnosticRepository.scenarios.filter {
            it.observableSigns.isNotEmpty() || it.systemExplanation.isNotEmpty()
        }

        assertTrue(expanded.size >= 16)
        expanded.forEach { scenario ->
            assertFalse("${scenario.id}: observable signs", scenario.observableSigns.isEmpty())
            assertFalse("${scenario.id}: explanation", scenario.systemExplanation.isEmpty())
            assertFalse("${scenario.id}: consequences", scenario.operationalConsequences.isEmpty())
            assertFalse("${scenario.id}: feedback", scenario.feedbackPrompts.isEmpty())
            scenario.relatedScenarioIds.forEach { relatedId ->
                assertTrue("${scenario.id}: missing link $relatedId", DiagnosticRepository.scenario(relatedId) != null)
            }
            scenario.questions.forEach { question ->
                listOfNotNull(question.yesNextKey, question.noNextKey)
                    .filter { it != DiagnosticRepository.END_OF_FLOW }
                    .forEach { target ->
                        assertTrue(
                            "${scenario.id}: missing branch $target",
                            scenario.questions.any { it.key == target }
                        )
                    }
            }
        }
    }

    @Test
    fun branchingEngine_followsExplicitRouteAndStops() {
        val scenario = DiagnosticRepository.scenario("control-voltage-low")!!
        val first = scenario.questions.first()
        val second = DiagnosticRepository.nextQuestion(scenario, first.key, answerYes = true)
        val third = DiagnosticRepository.nextQuestion(scenario, second!!.key, answerYes = false)

        assertEquals("ctrl-section", second.key)
        assertEquals("ctrl-charge", third!!.key)
        assertEquals(null, DiagnosticRepository.nextQuestion(scenario, third.key, answerYes = true))
    }

    @Test
    fun mainBreakerTreeSeparatesNoAttemptFromLoadRelatedTrip() {
        val scenario = DiagnosticRepository.scenario("gv-no-close")!!
        val first = scenario.questions.first()

        assertEquals("gvc-scope", DiagnosticRepository.nextQuestion(scenario, first.key, DiagnosticResponse.NO)?.key)
        assertEquals(null, DiagnosticRepository.nextQuestion(scenario, first.key, DiagnosticResponse.YES))
        assertEquals("gvc-voltage", DiagnosticRepository.nextQuestion(scenario, "gvc-scope", DiagnosticResponse.YES)?.key)
        assertEquals("gvc-attempt", DiagnosticRepository.nextQuestion(scenario, "gvc-voltage", DiagnosticResponse.NO)?.key)
    }

    @Test
    fun tractionTreeScoresScopeAndPositionSeparately() {
        val scenario = DiagnosticRepository.scenario("traction-no-assemble")!!
        assertEquals("tna-va2", DiagnosticRepository.nextQuestion(scenario, "tna-scope", DiagnosticResponse.YES)?.key)
        assertEquals(null, DiagnosticRepository.nextQuestion(scenario, "tna-va2", DiagnosticResponse.NO))
        assertEquals("tna-aux", DiagnosticRepository.nextQuestion(scenario, "tna-apparatus", DiagnosticResponse.YES)?.key)
    }

    @Test
    fun highVoltageScenarios_requireAuthorizedPersonnel() {
        val ids = setOf("pantograph-no-rise", "gv-no-close", "traction-no-assemble", "ekg-stuck", "protection-trip")

        DiagnosticRepository.scenarios.filter { it.id in ids }.forEach { scenario ->
            assertTrue(
                "${scenario.id} must contain an authorized-only boundary",
                scenario.checks.any { it.level == DiagnosticActionLevel.AUTHORIZED_ONLY }
            )
        }
    }

    @Test
    fun search_findsCommonEquipmentNames() {
        assertTrue(DiagnosticRepository.search("№395").any { it.id == "brakes-no-apply-release" })
        assertTrue(DiagnosticRepository.search("ЭКГ").any { it.id == "ekg-stuck" })
        assertTrue(DiagnosticRepository.search("БУРТ").any { it.id == "rheostatic-brake" })
        assertTrue(DiagnosticRepository.search("АЛСН").any { it.id == "alsn-epk" })
        assertTrue(DiagnosticRepository.search("АК-11Б").any { it.id == "compressor-no-start" })
        assertTrue(DiagnosticRepository.search("фазорасщепитель").any { it.id == "phase-splitter-no-start" })
        assertTrue(DiagnosticRepository.search("песок").any { it.id == "sanding-failure" })
    }

    @Test
    fun fireScenario_containsRequiredElectricalSafetyDistances() {
        val fire = DiagnosticRepository.scenarios.single { it.id == "smoke-fire-flashover" }
        val text = listOf(
            fire.immediateActions,
            fire.dangerSigns,
            fire.probableCauses,
            fire.prohibited,
            fire.stopConditions,
            fire.questions.flatMap { listOf(it.text, it.yesMeaning, it.noMeaning) },
            fire.checks.flatMap { listOf(it.action, it.expected, it.ifAbnormal) }
        ).flatten().joinToString(" ")

        assertTrue(text.contains("2 м"))
        assertTrue(text.contains("8 м"))
        assertTrue(text.contains("50 м"))
    }

    @Test
    fun extendedCatalogKeepsSafetyBoundaryAndBranchIntegrity() {
        assertTrue(DiagnosticExtendedCatalog.scenarios.size >= 50)
        DiagnosticExtendedCatalog.scenarios.forEach { scenario ->
            assertTrue("${scenario.id}: authorized boundary", scenario.checks.any { it.level == DiagnosticActionLevel.AUTHORIZED_ONLY })
            assertTrue("${scenario.id}: safe stop", scenario.checks.any { it.level == DiagnosticActionLevel.SAFE_STOP })
            assertTrue("${scenario.id}: bypass prohibition", scenario.prohibited.any { it.contains("Шунтировать") })
            scenario.questions.flatMap { listOfNotNull(it.yesNextKey, it.noNextKey, it.unknownNextKey) }
                .filter { it != DiagnosticRepository.END_OF_FLOW }
                .forEach { target -> assertTrue("${scenario.id}: missing $target", scenario.questions.any { it.key == target }) }
        }
    }

    @Test
    fun equipmentAtlasHasFortyNodesAndOnlyValidScenarioLinks() {
        val equipment = Vl80sObservationCatalog.equipment
        assertTrue(equipment.size >= 46)
        assertEquals(equipment.size, equipment.map { it.id }.distinct().size)
        equipment.forEach { item ->
            assertTrue(item.title.isNotBlank())
            assertTrue(item.purpose.isNotBlank())
            assertTrue(item.location.isNotBlank())
            item.scenarioIds.forEach { scenarioId ->
                assertTrue("${item.id}: missing scenario $scenarioId", DiagnosticRepository.scenario(scenarioId) != null)
            }
        }
    }

    @Test
    fun priorityBrakeAndAuxiliaryScenariosUseSubjectSpecificTrees() {
        val priorityIds = setOf(
            "aux-common-loss",
            "fan-low-airflow",
            "compressor-long-run",
            "brake-pipe-no-charge",
            "equalizing-reservoir-mismatch",
            "independent-brake-no-apply",
            "independent-brake-no-release",
            "brake-cylinder-imbalance"
        )

        priorityIds.forEach { id ->
            val scenario = DiagnosticRepository.scenario(id)!!
            assertTrue("$id: question depth", scenario.questions.size >= 3)
            assertTrue("$id: authorized boundary", scenario.checks.any { it.level == DiagnosticActionLevel.AUTHORIZED_ONLY })
            assertTrue("$id: tailored causes", scenario.probableCauses.size >= 5)
            assertTrue("$id: related routes", scenario.relatedScenarioIds.size >= 4)
            assertTrue("$id: no generic first question", !scenario.questions.first().text.contains("одной секции, тележке или группе"))
        }
    }

    @Test
    fun observationsAndEquipmentOnlyLinkToExistingEntities() {
        val scenarioIds = DiagnosticRepository.scenarios.map { it.id }.toSet()
        val equipmentIds = Vl80sObservationCatalog.equipment.map { it.id }.toSet()

        Vl80sObservationCatalog.observations.forEach { observation ->
            observation.scenarioIds.forEach { id ->
                assertTrue("${observation.id}: missing scenario $id", id in scenarioIds)
            }
            observation.equipmentIds.forEach { id ->
                assertTrue("${observation.id}: missing equipment $id", id in equipmentIds)
            }
        }
    }

    @Test
    fun completionPassAddsDistinctRoutesAndAtlasBacklinks() {
        val completionIds = setOf(
            "brake-lock-state-mismatch",
            "main-reservoir-safety-valve",
            "aux-machine-single-trip",
            "ventilation-duct-damage",
            "rheostatic-resistor-overheat",
            "pneumatic-instrument-line-leak"
        )
        val equipmentLinks = Vl80sObservationCatalog.equipment.flatMap { it.scenarioIds }.toSet()

        completionIds.forEach { id ->
            val scenario = DiagnosticRepository.scenario(id)!!
            assertEquals("$id: three-step route", 3, scenario.questions.size)
            assertTrue("$id: atlas backlink", id in equipmentLinks)
            assertTrue("$id: related routes", scenario.relatedScenarioIds.size >= 4)
        }
    }
    @Test
    fun diagnosticAnswersProduceDifferentCurrentAssessments() {
        val scenario = DiagnosticRepository.scenario("gv-no-close")!!
        val start = DiagnosticDecisionEngine.start(scenario)
        val yes = DiagnosticDecisionEngine.answer(scenario, start, DiagnosticResponse.YES)
        val no = DiagnosticDecisionEngine.answer(scenario, start, DiagnosticResponse.NO)

        assertTrue(yes.state.answers.last().conclusion.isNotBlank())
        assertTrue(no.state.answers.last().conclusion.isNotBlank())
        assertTrue(yes.state.answers.last().conclusion != no.state.answers.last().conclusion)
    }

}
