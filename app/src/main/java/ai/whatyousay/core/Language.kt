package ai.whatyousay.core

/** A language the app can translate. `code` is ISO 639-1 where possible. */
data class Language(val code: String, val name: String, val nativeName: String)

/**
 * The languages What You Say targets first. This is the intersection of the
 * Hunyuan Hy-MT2 and whisper coverage that matters most for travel and field use.
 * Breadth beyond this comes from the optional NLLB-200 pack.
 */
object Languages {
    val EN = Language("en", "English", "English")
    val ES = Language("es", "Spanish", "Espanol")
    val FR = Language("fr", "French", "Francais")
    val DE = Language("de", "German", "Deutsch")
    val IT = Language("it", "Italian", "Italiano")
    val PT = Language("pt", "Portuguese", "Portugues")
    val ZH = Language("zh", "Chinese", "Zhongwen")
    val JA = Language("ja", "Japanese", "Nihongo")
    val KO = Language("ko", "Korean", "Hangugeo")
    val AR = Language("ar", "Arabic", "Arabiyya")
    val RU = Language("ru", "Russian", "Russkiy")
    val HI = Language("hi", "Hindi", "Hindi")
    val TR = Language("tr", "Turkish", "Turkce")
    val VI = Language("vi", "Vietnamese", "Tieng Viet")
    val TH = Language("th", "Thai", "Thai")
    val UK = Language("uk", "Ukrainian", "Ukrainska")

    val all: List<Language> = listOf(EN, ES, FR, DE, IT, PT, ZH, JA, KO, AR, RU, HI, TR, VI, TH, UK)

    fun byCode(code: String): Language? = all.firstOrNull { it.code.equals(code, ignoreCase = true) }
}

/** A directed translation pair. */
data class LanguagePair(val source: Language, val target: Language) {
    fun swapped(): LanguagePair = LanguagePair(target, source)

    override fun toString(): String = "${source.code}->${target.code}"
}
