package ru.railbrake.calculator.core

data class DiagnosticApplicability(
    val families: Set<String> = emptySet(),
    val profiles: Set<String> = emptySet(),
    val variantSelectionRequired: Boolean = false,
    val lateProfiles: String = ""
)

data class DiagnosticSourceReference(
    val sourceId: String = "",
    val document: String = "",
    val locator: String = "",
    val role: String = "",
    val kind: DiagnosticSourceKind = DiagnosticSourceKind.UNKNOWN,
    val version: DiagnosticSourceVersion = DiagnosticSourceVersion()
)

enum class DiagnosticUserFacingPolicy {
    UNSPECIFIED,
    TRIAGE_ONLY_NO_REPAIR,
    SOURCE_AND_PROFILE_REQUIRED,
    SAFETY_GATE_REQUIRED,
    EMERGENCY_SOURCE_BOUND,
    UNKNOWN;

    companion object {
        fun parse(value: String?): DiagnosticUserFacingPolicy {
            val normalized = value.orEmpty().trim().uppercase()
            if (normalized.isBlank()) return UNSPECIFIED
            return entries.firstOrNull { it.name == normalized } ?: UNKNOWN
        }
    }
}

data class DiagnosticActionMetadata(
    val riskClass: String = "",
    val userFacingPolicy: DiagnosticUserFacingPolicy = DiagnosticUserFacingPolicy.UNSPECIFIED,
    val rawUserFacingPolicy: String = "",
    val sourceBound: Boolean = false
)

data class DiagnosticPolicyContext(
    val selectedFamily: String? = null,
    val selectedProfileId: String? = null,
    val profileConfirmed: Boolean = false,
    val safetyGateConfirmed: Boolean = false
)

data class DiagnosticPolicyDecision(
    val allowed: Boolean,
    val message: String
)

object DiagnosticPolicyEngine {
    fun evaluate(
        applicability: DiagnosticApplicability,
        action: DiagnosticActionMetadata,
        context: DiagnosticPolicyContext
    ): DiagnosticPolicyDecision {
        val policy = action.userFacingPolicy

        if (policy == DiagnosticUserFacingPolicy.UNKNOWN) {
            return blocked("Для этого действия задана неизвестная политика безопасности. Показ действия заблокирован.")
        }

        if (
            policy == DiagnosticUserFacingPolicy.UNSPECIFIED &&
            action.riskClass.isNotBlank() &&
            !action.riskClass.equals("normal", ignoreCase = true)
        ) {
            return blocked("Для потенциально опасного действия не задана явная политика показа. Показ действия заблокирован.")
        }

        return when (policy) {
            DiagnosticUserFacingPolicy.UNSPECIFIED,
            DiagnosticUserFacingPolicy.TRIAGE_ONLY_NO_REPAIR -> allowed()

            DiagnosticUserFacingPolicy.SOURCE_AND_PROFILE_REQUIRED -> {
                when {
                    !action.sourceBound ->
                        blocked("Действие не привязано к подтверждённому источнику.")
                    !profileCompatible(applicability, context) ->
                        blocked("Действие зависит от исполнения локомотива. Сначала подтвердите профиль оборудования.")
                    else -> allowed()
                }
            }

            DiagnosticUserFacingPolicy.SAFETY_GATE_REQUIRED -> {
                when {
                    !action.sourceBound ->
                        blocked("Опасное действие не привязано к подтверждённому источнику.")
                    !profileCompatible(applicability, context) ->
                        blocked("Опасное действие зависит от исполнения локомотива. Сначала подтвердите профиль оборудования.")
                    !context.safetyGateConfirmed ->
                        blocked("Действие требует отдельного подтверждения условий безопасности и допуска.")
                    else -> allowed()
                }
            }

            DiagnosticUserFacingPolicy.EMERGENCY_SOURCE_BOUND -> {
                when {
                    !action.sourceBound ->
                        blocked("Аварийное действие не привязано к подтверждённому источнику.")
                    applicability.variantSelectionRequired && !profileCompatible(applicability, context) ->
                        blocked("Аварийный порядок зависит от исполнения. Требуется подтверждённый профиль.")
                    else -> allowed()
                }
            }

            DiagnosticUserFacingPolicy.UNKNOWN ->
                blocked("Неизвестная политика безопасности.")
        }
    }

    private fun profileCompatible(
        applicability: DiagnosticApplicability,
        context: DiagnosticPolicyContext
    ): Boolean {
        val selectedProfile = context.selectedProfileId ?: return false
        if (!context.profileConfirmed) return false

        if (
            context.selectedFamily != null &&
            applicability.families.isNotEmpty() &&
            context.selectedFamily !in applicability.families
        ) {
            return false
        }

        val requestedProfiles = applicability.profiles
            .map { it.trim().lowercase() }
            .toSet()
        val selectedProfileKey = selectedProfile.trim().lowercase()

        return requestedProfiles.isEmpty() ||
            "all_confirmed_profiles" in requestedProfiles ||
            "profile_required" in requestedProfiles ||
            selectedProfileKey in requestedProfiles
    }

    private fun allowed() = DiagnosticPolicyDecision(
        allowed = true,
        message = ""
    )

    private fun blocked(message: String) = DiagnosticPolicyDecision(
        allowed = false,
        message = message
    )
}
