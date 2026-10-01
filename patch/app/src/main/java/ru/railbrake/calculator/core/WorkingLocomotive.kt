package ru.railbrake.calculator.core

/** Day-to-day search/navigation scope. This is never a confirmed equipment profile. */
enum class WorkingLocomotive(
    val title: String,
    val family: TechnicalFamily,
    val variantId: String? = null
) {
    VL80S("ВЛ80С", TechnicalFamily.VL80S),
    ERMAK_2ES5K("2ЭС5К «Ермак»", TechnicalFamily.ERMAK, "2ES5K"),
    ERMAK_3ES5K("3ЭС5К «Ермак»", TechnicalFamily.ERMAK, "3ES5K"),
    CHME3("ЧМЭ3", TechnicalFamily.CHME3),
    CHME3T("ЧМЭ3Т", TechnicalFamily.CHME3T),
    CHME3E("ЧМЭ3Э", TechnicalFamily.CHME3E);

    companion object {
        fun fromStored(value: String?): WorkingLocomotive? = entries.firstOrNull { it.name == value }

        fun explicitlyNamed(text: String): WorkingLocomotive? {
            val normalized = text.lowercase().replace('ё', 'е')
            return when {
                "чмэ3э" in normalized || "чмэ 3 э" in normalized || "чмэ три э" in normalized -> CHME3E
                "чмэ3т" in normalized || "чмэ 3 т" in normalized || "чмэ три т" in normalized -> CHME3T
                "чмэ3" in normalized || "чмэ 3" in normalized || "чмэ три" in normalized -> CHME3
                "3эс5к" in normalized -> ERMAK_3ES5K
                "2эс5к" in normalized -> ERMAK_2ES5K
                "вл80" in normalized -> VL80S
                else -> null // «Ермак» alone does not establish a variant.
            }
        }
    }
}
