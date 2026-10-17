package com.example.barcodekeyboard

import com.example.barcodekeyboard.ui.keyboard.KeyVariants
import org.junit.Assert.*
import org.junit.Test

class KeyVariantsTest {

    @Test
    fun testLowercaseVowelVariants() {
        val aVariants = KeyVariants.getVariants("a", isShifted = false)
        assertNotNull(aVariants)
        assertTrue(aVariants.contains("a"))
        assertTrue(aVariants.contains("á"))
        assertTrue(aVariants.contains("à"))
        assertTrue(aVariants.contains("â"))
    }

    @Test
    fun testUppercaseVowelVariants() {
        val aVariantsUpper = KeyVariants.getVariants("a", isShifted = true)
        assertNotNull(aVariantsUpper)
        assertTrue(aVariantsUpper.contains("A"))
        assertTrue(aVariantsUpper.contains("Á"))
        assertTrue(aVariantsUpper.contains("À"))
    }

    @Test
    fun testNumberVariants() {
        val zeroVariants = KeyVariants.getVariants("0", isShifted = false)
        assertNotNull(zeroVariants)
        assertTrue(zeroVariants.contains("0"))
        assertTrue(zeroVariants.contains("°"))
    }

    @Test
    fun testSymbolVariants() {
        val dollarVariants = KeyVariants.getVariants("$", isShifted = false)
        assertNotNull(dollarVariants)
        assertTrue(dollarVariants.contains("$"))
        assertTrue(dollarVariants.contains("€"))
        assertTrue(dollarVariants.contains("£"))
        assertTrue(dollarVariants.contains("¥"))
    }

    @Test
    fun testHintAppended() {
        val qVariantsWith1Hint = KeyVariants.getVariants("q", hint = "1", isShifted = false)
        assertTrue(qVariantsWith1Hint.contains("1"))
    }

    @Test
    fun testFallbackForUnknownKey() {
        val unknown = KeyVariants.getVariants("~", isShifted = false)
        assertTrue(unknown.isEmpty())
    }
}
