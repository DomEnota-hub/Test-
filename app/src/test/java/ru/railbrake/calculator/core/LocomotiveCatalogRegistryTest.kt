package ru.railbrake.calculator.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocomotiveCatalogRegistryTest {
    @Test
    fun browsingFamiliesAreCentralizedAndComplete() {
        assertEquals(
            listOf(
                TechnicalFamily.VL80S,
                TechnicalFamily.ERMAK,
                TechnicalFamily.CHME3,
                TechnicalFamily.CHME3T,
                TechnicalFamily.CHME3E
            ),
            LocomotiveCatalogRegistry.families
        )
        assertEquals(
            LocomotiveCatalogRegistry.profiles.size,
            LocomotiveCatalogRegistry.profiles.map { it.id }.distinct().size
        )
        assertEquals(
            LocomotiveCatalogRegistry.families.size,
            LocomotiveCatalogRegistry.families.distinct().size
        )
    }

    @Test
    fun workingOptionsAndBrowsingFamiliesAreDifferentConcepts() {
        assertEquals(WorkingLocomotive.entries.toList(), LocomotiveCatalogRegistry.workingOptions)
        assertEquals(6, LocomotiveCatalogRegistry.workingOptions.size)
        assertEquals(5, LocomotiveCatalogRegistry.families.size)

        val working = WorkingLocomotive.CHME3
        val viewing = TechnicalFamily.ERMAK
        assertNotEquals(working.family, viewing)
        assertEquals(TechnicalFamily.CHME3, working.family)
        assertEquals(TechnicalFamily.ERMAK, viewing)
    }

    @Test
    fun savedViewingFamilyWinsWithoutChangingWorkingLocomotive() {
        val working = WorkingLocomotive.CHME3T
        val viewing = LocomotiveCatalogRegistry.initialViewingFamily(
            savedViewingFamily = TechnicalFamily.VL80S.name,
            workingLocomotive = working
        )

        assertEquals(TechnicalFamily.VL80S, viewing)
        assertEquals(TechnicalFamily.CHME3T, working.family)
    }

    @Test
    fun workingFamilySeedsViewOnlyWhenNoViewingContextExists() {
        assertEquals(
            TechnicalFamily.CHME3E,
            LocomotiveCatalogRegistry.initialViewingFamily(null, WorkingLocomotive.CHME3E)
        )
        assertTrue(LocomotiveCatalogRegistry.profile(TechnicalFamily.CHME3E).workingOptions.contains(WorkingLocomotive.CHME3E))
    }
}
