package ai.whatyousay.core

/** One completed exchange in a conversation: what was heard and what was spoken back. */
data class Turn(
    val source: Language,
    val target: Language,
    val heard: String,
    val spoken: String,
    val at: Long,
)

enum class ConvStatus { IDLE, LISTENING, WORKING, SPEAKING }

/**
 * Pure orchestration for hands-free, two-way translation.
 *
 * It holds no audio, calls no models, and imports nothing from Android, so it is
 * fully unit-testable on the JVM. The ViewModel drives it: feed it a finalized
 * transcription, it tells you which direction to translate, then you hand back the
 * result and it records the turn and advances the state machine.
 *
 * The cleverness is direction detection. With the phone between two people, the
 * engine matches the detected language to either side of the configured pair so
 * neither speaker has to press anything.
 */
class ConversationEngine(initialPair: LanguagePair, private val clock: () -> Long = { 0L }) {

    var pair: LanguagePair = initialPair
        private set

    var status: ConvStatus = ConvStatus.IDLE
        private set

    private val history = ArrayDeque<Turn>()
    val turns: List<Turn> get() = history.toList()

    fun start() {
        status = ConvStatus.LISTENING
    }

    fun stop() {
        status = ConvStatus.IDLE
    }

    fun setPair(newPair: LanguagePair) {
        pair = newPair
    }

    /**
     * Decide the translation direction for a finalized utterance.
     *
     * Direction hinges on which side actually spoke. Whisper's audio-based guess
     * ([detected]) is unreliable on short utterances, so we first identify the
     * language from the transcript itself, constrained to the two languages of the
     * pair, and only fall back to Whisper's guess when the text is inconclusive.
     * If the spoken language matches the target side, the other person is talking,
     * so we translate back toward the source. Otherwise the source side is talking.
     * Returns null when there is nothing to translate.
     */
    fun directionFor(text: String, detected: Language?): LanguagePair? {
        if (text.isBlank()) return null
        status = ConvStatus.WORKING
        val spoken = LanguageId.identify(text, listOf(pair.source, pair.target)) ?: detected
        return if (spoken?.code == pair.target.code) pair.swapped() else pair
    }

    /** Record a completed translation as a turn and move to speaking. */
    fun commit(result: TranslationResult): Turn {
        val turn = Turn(
            source = result.pair.source,
            target = result.pair.target,
            heard = result.sourceText,
            spoken = result.translatedText,
            at = clock(),
        )
        history.addLast(turn)
        status = ConvStatus.SPEAKING
        return turn
    }

    /** Synthesis finished. Return to listening unless the session was stopped. */
    fun ready() {
        if (status != ConvStatus.IDLE) status = ConvStatus.LISTENING
    }

    fun clearHistory() = history.clear()
}
