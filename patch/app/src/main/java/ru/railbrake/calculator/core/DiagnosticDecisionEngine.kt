package ru.railbrake.calculator.core

data class DiagnosticAnswerRecord(
    val questionKey: String,
    val questionText: String,
    val response: DiagnosticResponse,
    val conclusion: String
)

data class DiagnosticFlowState(
    val scenarioId: String,
    val currentQuestionKey: String?,
    val answers: List<DiagnosticAnswerRecord> = emptyList(),
    val candidateScores: Map<String, Int> = emptyMap()
)

data class DiagnosticFlowResult(
    val state: DiagnosticFlowState,
    val nextQuestion: DiagnosticQuestion?,
    val leadingCauses: List<DiagnosticCause>
)

/** Pure Kotlin engine: одинаково используется быстрым режимом, подробной карточкой и тренажёром. */
object DiagnosticDecisionEngine {
    fun start(scenario: DiagnosticScenario): DiagnosticFlowState = DiagnosticFlowState(
        scenarioId = scenario.id,
        currentQuestionKey = scenario.questions.firstOrNull()?.key
    )

    fun answer(
        scenario: DiagnosticScenario,
        state: DiagnosticFlowState,
        response: DiagnosticResponse
    ): DiagnosticFlowResult {
        val question = scenario.questions.firstOrNull { it.key == state.currentQuestionKey }
            ?: return result(scenario, state)
        val conclusion = DiagnosticRepository.meaning(question, response)
        val updatedScores = state.candidateScores.toMutableMap()
        DiagnosticRepository.candidateCauseIds(question, response).forEach { causeId ->
            updatedScores[causeId] = (updatedScores[causeId] ?: 0) + 1
        }
        val next = DiagnosticRepository.nextQuestion(scenario, question.key, response)
        val updated = state.copy(
            currentQuestionKey = next?.key,
            answers = state.answers + DiagnosticAnswerRecord(question.key, question.text, response, conclusion),
            candidateScores = updatedScores
        )
        return result(scenario, updated)
    }

    fun result(scenario: DiagnosticScenario, state: DiagnosticFlowState): DiagnosticFlowResult {
        val next = scenario.questions.firstOrNull { it.key == state.currentQuestionKey }
        val causes = scenario.diagnosticCauses
            .sortedByDescending { state.candidateScores[it.id] ?: 0 }
            .filter { (state.candidateScores[it.id] ?: 0) > 0 }
        return DiagnosticFlowResult(state, next, causes)
    }
}
