package ru.railbrake.calculator.core

/**
 * Explicit UNKNOWN completion for legacy linear VL80S cards that have not yet
 * been replaced by DiagnosticDeepening.
 *
 * The allow-list is intentional: this is not a global runtime fallback.  Each
 * unresolved answer keeps both meanings of its concrete question and moves to
 * the next independent observation already defined by that scenario.  The
 * last question hands off to the scenario-specific causes, checks, limits and
 * report fields rendered by the card.
 */
internal object DiagnosticUnknownRouting {
    private val legacyLinearScenarioIds = setOf(
        "alsn-epk",
        "aux-machines",
        "brake-pipe-leak",
        "brakes-no-apply-release",
        "compressor-no-start",
        "compressor-pressure",
        "control-voltage-low",
        "fire-alarm-signal",
        "main-reservoir-leak",
        "mechanical-noise-heating",
        "motor-fan-failure",
        "persistent-wheel-slip",
        "phase-splitter-no-start",
        "protection-trip",
        "rectifier-group-fault",
        "rheostatic-brake",
        "sanding-failure",
        "section-control-loss",
        "smoke-fire-flashover",
        "traction-current-imbalance",
        "transformer-protection",
        "uncommanded-braking",
        "wheel-dragging"
    )

    fun enrich(scenario: DiagnosticScenario): DiagnosticScenario {
        if (scenario.id !in legacyLinearScenarioIds) return scenario
        return scenario.copy(
            questions = scenario.questions.mapIndexed { index, question ->
                val next = scenario.questions.getOrNull(index + 1)?.key ?: DiagnosticRepository.END_OF_FLOW
                question.copy(
                    unknownMeaning = buildString {
                        append("В сценарии «${scenario.title}» не удалось подтвердить признак «${question.text}». ")
                        append("Одновременно сохраняются оба направления: ${question.yesMeaning} ${question.noMeaning} ")
                        if (next == DiagnosticRepository.END_OF_FLOW) {
                            append("Доступных различающих вопросов больше нет; используйте приведённые в карточке возможные причины, разрешённые проверки, ограничения и поля доклада, не считая ни один вариант подтверждённым.")
                        } else {
                            append("Локализация продолжается по следующему независимому наблюдению; действия, требующие подтверждения этой ветви, не выполнять.")
                        }
                    },
                    unknownNextKey = next,
                    unknownCandidateCauseIds = (question.yesCandidateCauseIds + question.noCandidateCauseIds).distinct()
                )
            }
        )
    }
}
