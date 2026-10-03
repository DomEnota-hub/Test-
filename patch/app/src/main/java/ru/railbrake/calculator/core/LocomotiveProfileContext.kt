package ru.railbrake.calculator.core

enum class LocomotiveProfileResolution {
    EXACT,
    UNKNOWN_FAIL_CLOSED
}

data class LocomotiveProfileContext(
    val familyId: String,
    val profileId: String,
    val resolution: LocomotiveProfileResolution
) {
    val isExact: Boolean get() = resolution == LocomotiveProfileResolution.EXACT
    val failClosed: Boolean get() = !isExact
}

/**
 * Single runtime registry for working-locomotive profile resolution.
 *
 * The registry intentionally never returns null. A profile that is absent, blank,
 * belongs to another family, or has not been integrated yet resolves to an explicit
 * UNKNOWN_FAIL_CLOSED context. CI verifies this table against
 * docs/locomotives/manifests/locomotive_families.json so a newly registered profile
 * cannot silently become unreachable in runtime.
 */
object LocomotiveProfileRegistry {
    const val UNKNOWN_PROFILE_ID = "unknown"
    const val UNKNOWN_FAMILY_ID = "unknown-family"

    internal val registeredProfiles: Map<String, String> = linkedMapOf(
        "chme3-base" to "chme3-family",
        "chme3t-rheostatic" to "chme3-family",
        "chme3e-electronic" to "chme3-family",
        "tem2-base" to "tem2-family",
        "tem2u-improved" to "tem2-family"
    )

    fun resolve(profileId: String): LocomotiveProfileContext {
        val normalized = profileId.trim()
        val familyId = registeredProfiles[normalized]
        return if (familyId != null) {
            LocomotiveProfileContext(
                familyId = familyId,
                profileId = normalized,
                resolution = LocomotiveProfileResolution.EXACT
            )
        } else {
            unknown()
        }
    }

    fun resolve(profileId: String, expectedFamilyId: String): LocomotiveProfileContext {
        val expected = expectedFamilyId.trim().ifEmpty { UNKNOWN_FAMILY_ID }
        val resolved = resolve(profileId)
        return if (resolved.isExact && resolved.familyId == expected) {
            resolved
        } else {
            unknown(expected)
        }
    }

    fun fromTechnicalFamily(family: TechnicalFamily): LocomotiveProfileContext = when (family) {
        TechnicalFamily.CHME3 -> resolve("chme3-base", "chme3-family")
        TechnicalFamily.CHME3T -> resolve("chme3t-rheostatic", "chme3-family")
        TechnicalFamily.CHME3E -> resolve("chme3e-electronic", "chme3-family")
        TechnicalFamily.TEM2 -> resolve("tem2-base", "tem2-family")
        TechnicalFamily.TEM2U -> resolve("tem2u-improved", "tem2-family")
        TechnicalFamily.VL80S -> unknown("vl80s-family")
        TechnicalFamily.ERMAK -> unknown("ermak-family")
    }

    fun isRegisteredExact(context: LocomotiveProfileContext): Boolean =
        context.isExact && registeredProfiles[context.profileId] == context.familyId

    private fun unknown(familyId: String = UNKNOWN_FAMILY_ID): LocomotiveProfileContext =
        LocomotiveProfileContext(
            familyId = familyId,
            profileId = UNKNOWN_PROFILE_ID,
            resolution = LocomotiveProfileResolution.UNKNOWN_FAIL_CLOSED
        )
}
