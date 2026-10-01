package ru.railbrake.calculator.core

/** Permanent presentation boundary: internal technical identifiers are routing keys, never UI text. */
object UserFacingTechnicalPolicy {
    private val internalIdToken = Regex(
        pattern = "(?i)(?<![\\p{L}\\p{N}_])(?:CHME3E|CHME3T|CHME3|VL80|VL|ER|SYS|SAFETY)(?:-[A-Z0-9_]+)+(?![\\p{L}\\p{N}_])"
    )
    private val internalRouteToken = Regex(
        pattern = "(?i)(?<![\\p{L}\\p{N}_])(?:route|profile|variant)_[a-z0-9_]+(?![\\p{L}\\p{N}_])"
    )

    fun containsInternalReference(value: String): Boolean {
        val text = value.trim()
        if (text.isEmpty()) return false
        return internalIdToken.containsMatchIn(text) || internalRouteToken.containsMatchIn(text)
    }

    fun isInternalReference(value: String): Boolean {
        val text = value.trim()
        if (text.isEmpty()) return false
        return internalIdToken.matches(text) || internalRouteToken.matches(text)
    }

    /**
     * Last-resort guard for user-facing strings. Structured resolvers should normally
     * replace references with titles before this point. If a raw key still reaches the
     * boundary, the unsafe fragment is removed rather than displayed.
     */
    fun sanitize(value: String): String? {
        var result = value
            .replace(internalIdToken, "")
            .replace(internalRouteToken, "")
            .replace(Regex("\\s+•\\s+•\\s+"), " • ")
            .replace(Regex("\\s{2,}"), " ")
            .trim(' ', '•', '—', '-', ':')
            .trim()
        if (result.isBlank()) return null
        if (containsInternalReference(result)) return null
        return result
    }
}

data class TechnicalSourcePresentation(
    val title: String,
    val documentDetails: String? = null,
    val provenance: String? = null
) {
    init {
        require(title.isNotBlank())
        require(!UserFacingTechnicalPolicy.containsInternalReference(title))
    }

    fun lines(): List<String> = listOfNotNull(
        title,
        documentDetails?.takeIf(String::isNotBlank),
        provenance?.takeIf(String::isNotBlank)
    )
}

data class TechnicalRelationPresentation(
    val fromTitle: String,
    val toTitle: String,
    val meaning: String
) {
    init {
        require(fromTitle.isNotBlank() && toTitle.isNotBlank() && meaning.isNotBlank())
        require(!UserFacingTechnicalPolicy.containsInternalReference(fromTitle))
        require(!UserFacingTechnicalPolicy.containsInternalReference(toTitle))
    }

    val title: String get() = "$fromTitle → $toTitle"
}
