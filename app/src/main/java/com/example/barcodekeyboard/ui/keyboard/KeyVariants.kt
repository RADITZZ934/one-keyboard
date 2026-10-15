package com.example.barcodekeyboard.ui.keyboard

import java.util.Locale

/**
 * Key variants dictionary for long-press sub-menus (accents, diacritics, and symbols),
 * styled after Material You / Gboard / FUTO Keyboard.
 */
object KeyVariants {

    private val BASE_VARIANTS: Map<String, List<String>> = mapOf(
        // Vowels
        "a" to listOf("a", "á", "à", "â", "ä", "æ", "ã", "å", "ā"),
        "e" to listOf("e", "é", "è", "ê", "ë", "ē", "ė", "ę"),
        "i" to listOf("i", "í", "ì", "î", "ï", "ī", "ı"),
        "o" to listOf("o", "ó", "ò", "ô", "ö", "õ", "œ", "ø", "ō"),
        "u" to listOf("u", "ú", "ù", "û", "ü", "ū", "ů"),
        "y" to listOf("y", "ý", "ÿ"),

        // Consonants
        "b" to listOf("b", "β"),
        "c" to listOf("c", "ç", "ć", "č"),
        "d" to listOf("d", "ð", "ď"),
        "g" to listOf("g", "ğ", "ġ"),
        "k" to listOf("k", "κ"),
        "l" to listOf("l", "ł", "ľ"),
        "n" to listOf("n", "ñ", "ń"),
        "r" to listOf("r", "ř"),
        "s" to listOf("s", "ß", "ś", "š", "$"),
        "t" to listOf("t", "ť", "þ"),
        "w" to listOf("w", "ŵ"),
        "z" to listOf("z", "ž", "ź", "ż"),

        // Numbers
        "0" to listOf("0", "°", "∅", "ⁿ", "₀"),
        "1" to listOf("1", "¹", "₁", "½", "⅓", "¼"),
        "2" to listOf("2", "²", "₂", "⅔"),
        "3" to listOf("3", "³", "₃", "¾", "⅜"),
        "4" to listOf("4", "⁴", "₄"),
        "5" to listOf("5", "⁵", "₅", "⅝"),
        "6" to listOf("6", "⁶", "₆"),
        "7" to listOf("7", "⁷", "₇", "⅞"),
        "8" to listOf("8", "⁸", "₈", "∞"),
        "9" to listOf("9", "⁹", "₉"),

        // Punctuation & Symbols
        "?" to listOf("?", "¿", "‽"),
        "!" to listOf("!", "¡"),
        "$" to listOf("$", "€", "£", "¥", "₹", "¢", "₩", "₽"),
        "&" to listOf("&", "§"),
        "%" to listOf("%", "‰"),
        "=" to listOf("=", "≠", "≈", "±", "≤", "≥"),
        "+" to listOf("+", "±", "∓"),
        "-" to listOf("-", "–", "—", "•"),
        "*" to listOf("*", "★", "†", "‡"),
        "/" to listOf("/", "\\"),
        "\"" to listOf("\"", "“", "”", "«", "»"),
        "'" to listOf("'", "‘", "’", "`"),
        "(" to listOf("(", "[", "{", "<"),
        ")" to listOf(")", "]", "}", ">")
    )

    /**
     * Returns variant characters for [char]. If [hint] is present (e.g. secondary symbol/number),
     * it is appended to the variant list so the user can easily select it via long-press.
     */
    fun getVariants(char: String, hint: String? = null, isShifted: Boolean = false): List<String> {
        val lower = char.toLowerCase(Locale.ROOT)
        val variants = BASE_VARIANTS[lower]?.toMutableList() ?: mutableListOf(char)

        if (!variants.contains(char)) {
            variants.add(0, char)
        }

        if (hint != null && !variants.contains(hint)) {
            variants.add(hint)
        }

        // If no alternative variants beyond the character itself and no hint, return empty
        if (variants.size <= 1 && hint == null) {
            return emptyList()
        }

        val shouldUppercase = isShifted || (char.isNotEmpty() && char.first().isUpperCase())
        return if (shouldUppercase) {
            variants.map { it.toUpperCase(Locale.ROOT) }
        } else {
            variants
        }
    }
}
