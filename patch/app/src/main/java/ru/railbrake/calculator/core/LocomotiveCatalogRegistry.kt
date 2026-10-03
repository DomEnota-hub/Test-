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
        LocomotiveCatalogProfile(
            id = "vl80s",
            family = TechnicalFamily.VL80S,
            title = TechnicalFamily.VL80S.title,
            subtitle = TechnicalFamily.VL80S.subtitle,
            workingOptions = listOf(WorkingLocomotive.VL80S)
        ),
        LocomotiveCatalogProfile(
            id = "ermak",
            family = TechnicalFamily.ERMAK,
            title = TechnicalFamily.ERMAK.title,
            subtitle = TechnicalFamily.ERMAK.subtitle,
            workingOptions = listOf(WorkingLocomotive.ERMAK_2ES5K, WorkingLocomotive.ERMAK_3ES5K)
        ),
        LocomotiveCatalogProfile(
            id = "chme3",
            family = TechnicalFamily.CHME3,
            title = TechnicalFamily.CHME3.title,
            subtitle = TechnicalFamily.CHME3.subtitle,
            workingOptions = listOf(WorkingLocomotive.CHME3)
        ),
        LocomotiveCatalogProfile(
            id = "chme3t",
            family = TechnicalFamily.CHME3T,
            title = TechnicalFamily.CHME3T.title,
            subtitle = TechnicalFamily.CHME3T.subtitle,
            workingOptions = listOf(WorkingLocomotive.CHME3T)
        ),
        LocomotiveCatalogProfile(
            id = "chme3e",
            family = TechnicalFamily.CHME3E,
            title = TechnicalFamily.CHME3E.title,
            subtitle = TechnicalFamily.CHME3E.subtitle,
            workingOptions = listOf(WorkingLocomotive.CHME3E)
        ),
        LocomotiveCatalogProfile(
            id = "tem2", family = TechnicalFamily.TEM2,
            title = TechnicalFamily.TEM2.title, subtitle = TechnicalFamily.TEM2.subtitle,
            workingOptions = listOf(WorkingLocomotive.TEM2)
        ),
        LocomotiveCatalogProfile(
            id = "tem2u", family = TechnicalFamily.TEM2U,
            title = TechnicalFamily.TEM2U.title, subtitle = TechnicalFamily.TEM2U.subtitle,
            workingOptions = listOf(WorkingLocomotive.TEM2U)
        )
    )

    val families: List<TechnicalFamily> = profiles.map(LocomotiveCatalogProfile::family)

    val workingOptions: List<WorkingLocomotive> =
        profiles.flatMap(LocomotiveCatalogProfile::workingOptions).distinct()

    fun profile(family: TechnicalFamily): LocomotiveCatalogProfile =
        profiles.first { it.family == family }

    fun browsingFamilyOrDefault(value: String?, fallback: TechnicalFamily = TechnicalFamily.VL80S): TechnicalFamily =
        families.firstOrNull { it.name == value } ?: fallback

    fun initialViewingFamily(
        savedViewingFamily: String?,
        workingLocomotive: WorkingLocomotive?
    ): TechnicalFamily = families.firstOrNull { it.name == savedViewingFamily }
        ?: workingLocomotive?.family
        ?: families.first()
}
