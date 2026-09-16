package ru.railbrake.calculator.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperEasterEggTest {
    @Test
    fun exactDeveloperCombinationTriggersEasterEgg() {
        assertTrue(isDeveloperEasterEgg(massTons = 2381.0, axleCount = 999))
    }

    @Test
    fun anyDifferentValueDoesNotTriggerEasterEgg() {
        assertFalse(isDeveloperEasterEgg(massTons = 2381.001, axleCount = 999))
        assertFalse(isDeveloperEasterEgg(massTons = 2381.0, axleCount = 998))
        assertFalse(isDeveloperEasterEgg(massTons = 2381.0, axleCount = null))
    }

    @Test
    fun examQuestionsCodeIsSeparateAndExact() {
        assertTrue(isSecretExamAccessCode(massTons = 1000.0, axleCount = 2381))
        assertFalse(isSecretExamAccessCode(massTons = 2381.0, axleCount = 999))
        assertFalse(isSecretExamAccessCode(massTons = 1000.0, axleCount = 2380))
        assertFalse(isSecretExamAccessCode(massTons = 999.9, axleCount = 2381))
    }
}
