package ru.railbrake.calculator.core

/**
 * Single source of truth for locomotive series exposed by technical browsing UI.
 *
 * A browsing family is deliberately different from [WorkingLocomotive]:
 * - browsing changes only what the user is looking at;
 * - working selection is persisted from Home and scopes the assistant by default.
 *
 * Adding a new supported family here makes it available to shared selectors without
 * editing every screen separately. Variant-specific working choices may still map to
 * the same browsing family (for example 2ЭС5К/3ЭС5К -> Ермак).
 */
data class LocomotiveCatalogProfile(
    val id: String,
    val family: TechnicalFamily,
    val title: String,
    val subtitle: String,
    val workingOptions: List<WorkingLocomotive>
)

object LocomotiveCatalogRegistry {
    val profiles: List<LocomotiveCatalogProfile> = listOf(
        LocomotiveCatalogProfile("vl80s", TechnicalFamily.VL80S, TechnicalFamily.VL80S.title, TechnicalFamily.VL80S.subtitle, listOf(WorkingLocomotive.VL80S)),
        LocomotiveCatalogProfile("ermak", TechnicalFamily.ERMAK, TechnicalFamily.ERMAK.title, TechnicalFamily.ERMAK.subtitle, listOf(WorkingLocomotive.ERMAK_2ES5K, WorkingLocomotive.ERMAK_3ES5K)),
        LocomotiveCatalogProfile("chme3", TechnicalFamily.CHME3, TechnicalFamily.CHME3.title, TechnicalFamily.CHME3.subtitle, listOf(WorkingLocomotive.CHME3)),
        LocomotiveCatalogProfile("chme3t", TechnicalFamily.CHME3T, TechnicalFamily.CHME3T.title, TechnicalFamily.CHME3T.subtitle, listOf(WorkingLocomotive.CHME3T)),
        LocomotiveCatalogProfile("chme3e", TechnicalFamily.CHME3E, TechnicalFamily.CHME3E.title, TechnicalFamily.CHME3E.subtitle, listOf(WorkingLocomotive.CHME3E))
    )

    val families: List<TechnicalFamily> = profiles.map(LocomotiveCatalogProfile::family)
    val workingOptions: List<WorkingLocomotive> = profiles.flatMap(LocomotiveCatalogProfile::workingOptions).distinct()

    fun profile(family: TechnicalFamily): LocomotiveCatalogProfile = profiles.first { it.family == family }

    fun browsingFamilyOrDefault(value: String?, fallback: TechnicalFamily = TechnicalFamily.VL80S): TechnicalFamily =
        families.firstOrNull { it.name == value } ?: fallback

    fun initialViewingFamily(savedViewingFamily: String?, workingLocomotive: WorkingLocomotive?): TechnicalFamily =
        families.firstOrNull { it.name == savedViewingFamily } ?: workingLocomotive?.family ?: families.first()
}
