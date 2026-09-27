package ru.railbrake.calculator.core

enum class DiagnosticSourceKind {
    NORMATIVE,
    MANUFACTURER,
    LOCAL_INSTRUCTION,
    TRAINING,
    UNKNOWN;

    companion object {
        fun parse(value: String?): DiagnosticSourceKind {
            val normalized = value.orEmpty().trim().uppercase()
            if (normalized.isBlank()) return UNKNOWN
            return entries.firstOrNull { it.name == normalized } ?: UNKNOWN
        }
    }
}

enum class DiagnosticSourceVersionStatus {
    CURRENT_CONFIRMED,
    HISTORICAL,
    SUPERSEDED,
    REQUIRES_REVIEW,
    UNKNOWN;

    companion object {
        fun parse(value: String?): DiagnosticSourceVersionStatus {
            val normalized = value.orEmpty().trim().uppercase()
            if (normalized.isBlank()) return UNKNOWN
            return entries.firstOrNull { it.name == normalized } ?: UNKNOWN
        }
    }
}

data class DiagnosticSourceVersion(
    val versionLabel: String = "",
    val revision: String = "",
    val effectiveFrom: String = "",
    val effectiveTo: String = "",
    val verifiedAt: String = "",
    val status: DiagnosticSourceVersionStatus = DiagnosticSourceVersionStatus.UNKNOWN
) {
    fun hasExplicitVersionIdentity(): Boolean =
        versionLabel.isNotBlank() || revision.isNotBlank()
}

data class DiagnosticSourceAssessment(
    val usableForSourceBoundAction: Boolean,
    val message: String
)

object DiagnosticSourcePolicy {
    fun evaluate(reference: DiagnosticSourceReference): DiagnosticSourceAssessment {
        if (reference.sourceId.isBlank() || reference.document.isBlank()) {
            return blocked("Источник не имеет полного идентификатора документа.")
        }

        if (reference.kind !in setOf(DiagnosticSourceKind.NORMATIVE, DiagnosticSourceKind.MANUFACTURER)) {
            return blocked("Для источника не подтверждён нормативный или заводской тип.")
        }

        if (!reference.version.hasExplicitVersionIdentity()) {
            return blocked("Для источника не зафиксирована версия или редакция документа.")
        }

        if (reference.version.status != DiagnosticSourceVersionStatus.CURRENT_CONFIRMED) {
            return blocked("Актуальность версии источника не подтверждена.")
        }

        return DiagnosticSourceAssessment(
            usableForSourceBoundAction = true,
            message = ""
        )
    }

    fun evaluateAny(references: List<DiagnosticSourceReference>): DiagnosticSourceAssessment {
        if (references.isEmpty()) {
            return blocked("Для сценария не задан подтверждённый источник.")
        }

        if (references.any { evaluate(it).usableForSourceBoundAction }) {
            return DiagnosticSourceAssessment(
                usableForSourceBoundAction = true,
                message = ""
            )
        }

        return blocked("Среди источников сценария нет подтверждённой актуальной нормативной или заводской версии.")
    }

    private fun blocked(message: String) = DiagnosticSourceAssessment(
        usableForSourceBoundAction = false,
        message = message
    )
}
