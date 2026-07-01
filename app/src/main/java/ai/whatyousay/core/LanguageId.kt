package ai.whatyousay.core

/**
 * Lightweight, offline language identification over a small candidate set.
 *
 * Whisper reports the language it thinks it heard, but on short or simple audio
 * (a single greeting like "Bonjour") its all-languages guess is unreliable and
 * often collapses to English, which breaks two-way direction detection. This
 * identifier looks at the transcribed text and, constrained to just the two
 * languages of the active pair, decides which one was actually spoken.
 *
 * It is pure text and holds no state, so it runs on the JVM and is unit tested.
 * Non-Latin languages are settled by their script (Arabic, Cyrillic, Han, and so
 * on); Latin-script languages are scored by high-frequency function words plus a
 * small bonus for script-specific accents. A language with no profile contributes
 * no score, so an unrecognized text yields null and the caller keeps Whisper's
 * own guess rather than picking wrongly.
 */
object LanguageId {

    /** Return the candidate that [text] most likely is, or null when unsure. */
    fun identify(text: String, candidates: List<Language>): Language? {
        val cleaned = text.trim()
        if (cleaned.isEmpty() || candidates.isEmpty()) return null

        scriptOf(cleaned)?.let { script ->
            candidates.firstOrNull { it.code == script }?.let { return it }
        }

        val tokens = tokenize(cleaned)
        if (tokens.isEmpty()) return null

        var best: Language? = null
        var bestScore = 0
        var tie = false
        for (language in candidates) {
            val profile = WORDS[language.code] ?: continue
            val score = tokens.count { it in profile } + accentBonus(cleaned, language.code)
            when {
                score > bestScore -> {
                    bestScore = score
                    best = language
                    tie = false
                }
                score == bestScore && score > 0 -> tie = true
            }
        }
        return if (bestScore > 0 && !tie) best else null
    }

    /** Pick the language whose script dominates the text, when it is a scripted one. */
    private fun scriptOf(text: String): String? {
        var arabic = 0
        var cyrillic = 0
        var han = 0
        var kana = 0
        var hangul = 0
        var devanagari = 0
        var thai = 0
        for (ch in text) {
            when (ch.code) {
                in 0x0600..0x06FF -> arabic++
                in 0x0400..0x04FF -> cyrillic++
                in 0x4E00..0x9FFF -> han++
                in 0x3040..0x30FF -> kana++
                in 0xAC00..0xD7A3 -> hangul++
                in 0x0900..0x097F -> devanagari++
                in 0x0E00..0x0E7F -> thai++
            }
        }
        return when {
            kana > 0 -> "ja"
            hangul > 0 -> "ko"
            han > 0 -> "zh"
            arabic > 0 -> "ar"
            cyrillic > 0 -> "ru"
            devanagari > 0 -> "hi"
            thai > 0 -> "th"
            else -> null
        }
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase().split(Regex("[^\\p{L}']+")).filter { it.isNotEmpty() }

    private fun accentBonus(text: String, code: String): Int {
        val accents = ACCENTS[code] ?: return 0
        return text.count { it.lowercaseChar() in accents }
    }

    private val ACCENTS: Map<String, Set<Char>> = mapOf(
        "fr" to "éèêëàâçùûîïôœæ".toSet(),
        "es" to "áíóúñ¿¡".toSet(),
        "pt" to "ãõáâàéêíóôúç".toSet(),
        "de" to "äöüß".toSet(),
        "it" to "àèéìòù".toSet(),
    )

    /** High-frequency function words per language. Enough to separate the pair, not to parse it. */
    private val WORDS: Map<String, Set<String>> = mapOf(
        "en" to setOf(
            "the", "and", "is", "are", "you", "i", "to", "of", "a", "in", "it", "that", "this",
            "for", "on", "with", "hello", "hi", "thanks", "thank", "where", "how", "much", "please",
            "yes", "no", "good", "morning", "what", "do", "my", "me", "we", "your", "can", "will",
            "have", "was", "not", "he", "she", "they", "at", "there", "here", "goodbye", "sorry",
        ),
        "fr" to setOf(
            "le", "la", "les", "un", "une", "des", "et", "est", "sont", "vous", "je", "tu", "à",
            "de", "du", "en", "il", "elle", "que", "qui", "pour", "sur", "avec", "bonjour", "salut",
            "merci", "où", "comment", "combien", "s'il", "plaît", "oui", "non", "bon", "bonne",
            "matin", "quoi", "faire", "mon", "ma", "mes", "nous", "votre", "peux", "ça", "c'est",
            "au", "revoir", "pardon", "excusez", "moi", "être", "avez",
        ),
        "es" to setOf(
            "el", "la", "los", "las", "un", "una", "y", "es", "son", "usted", "yo", "de", "en",
            "que", "para", "con", "hola", "gracias", "dónde", "cómo", "cuánto", "por", "favor",
            "sí", "no", "bueno", "buenos", "días", "qué", "hacer", "mi", "nosotros", "adiós",
            "perdón", "buenas", "muy", "está", "soy",
        ),
        "de" to setOf(
            "der", "die", "das", "und", "ist", "sind", "sie", "ich", "du", "zu", "von", "in",
            "mit", "hallo", "danke", "wo", "wie", "viel", "bitte", "ja", "nein", "gut", "guten",
            "morgen", "was", "machen", "mein", "wir", "ihr", "können", "auf", "nicht", "tschüss",
        ),
        "it" to setOf(
            "il", "lo", "la", "gli", "le", "un", "una", "e", "è", "sono", "lei", "io", "di", "in",
            "che", "per", "con", "ciao", "grazie", "dove", "come", "quanto", "prego", "sì", "no",
            "buono", "buongiorno", "cosa", "fare", "mio", "noi", "vostro", "posso", "non", "arrivederci",
        ),
        "pt" to setOf(
            "o", "a", "os", "as", "um", "uma", "e", "é", "são", "você", "eu", "de", "em", "que",
            "para", "com", "olá", "obrigado", "onde", "como", "quanto", "por", "favor", "sim",
            "não", "bom", "dia", "fazer", "meu", "nós", "vosso", "posso", "adeus", "desculpe",
        ),
    )
}
