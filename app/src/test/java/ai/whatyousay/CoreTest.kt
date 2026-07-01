package ai.whatyousay

import ai.whatyousay.core.ConvStatus
import ai.whatyousay.core.ConversationEngine
import ai.whatyousay.core.LanguageId
import ai.whatyousay.core.LanguagePair
import ai.whatyousay.core.Languages
import ai.whatyousay.core.TranslationResult
import ai.whatyousay.engine.DeviceTier
import ai.whatyousay.engine.ModelCatalog
import ai.whatyousay.engine.Stage
import ai.whatyousay.engine.tierFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val GB = 1_000_000_000L

class CoreTest {
    private val enEs = LanguagePair(Languages.EN, Languages.ES)

    @Test
    fun directionDefaultsToSource() {
        val e = ConversationEngine(enEs)
        assertEquals(enEs, e.directionFor("hello", Languages.EN))
    }

    @Test
    fun directionFlipsWhenTargetSpeaks() {
        val e = ConversationEngine(enEs)
        assertEquals(enEs.swapped(), e.directionFor("hola", Languages.ES))
    }

    @Test
    fun blankProducesNoDirection() {
        val e = ConversationEngine(enEs)
        assertNull(e.directionFor("   ", null))
    }

    private val enFr = LanguagePair(Languages.EN, Languages.FR)

    @Test
    fun directionFlipsWhenTextIsTargetDespiteWrongDetection() {
        // Whisper mislabels short French audio as English; the transcript still
        // reads as French, so the direction must flip to FR->EN anyway.
        val e = ConversationEngine(enFr)
        assertEquals(enFr.swapped(), e.directionFor("Bonjour", Languages.EN))
    }

    @Test
    fun directionStaysSourceWhenTextIsSourceDespiteWrongDetection() {
        val e = ConversationEngine(enFr)
        assertEquals(enFr, e.directionFor("Hello there", Languages.FR))
    }

    @Test
    fun directionFallsBackToDetectionWhenTextInconclusive() {
        val e = ConversationEngine(enFr)
        assertEquals(enFr.swapped(), e.directionFor("42", Languages.FR))
        assertEquals(enFr, e.directionFor("42", Languages.EN))
    }

    @Test
    fun identifiesLatinLanguagesByFunctionWords() {
        val enFrList = listOf(Languages.EN, Languages.FR)
        assertEquals(Languages.FR, LanguageId.identify("Bonjour, comment allez-vous", enFrList))
        assertEquals(Languages.EN, LanguageId.identify("Where is the station", enFrList))
        assertEquals(Languages.FR, LanguageId.identify("Où est la gare", enFrList))
    }

    @Test
    fun identifiesByAccentWhenWordsTie() {
        assertEquals(Languages.FR, LanguageId.identify("français", listOf(Languages.EN, Languages.FR)))
    }

    @Test
    fun identifiesNonLatinByScript() {
        assertEquals(Languages.AR, LanguageId.identify("مرحبا", listOf(Languages.FR, Languages.AR)))
        assertEquals(Languages.RU, LanguageId.identify("привет", listOf(Languages.EN, Languages.RU)))
    }

    @Test
    fun returnsNullWhenNoCandidateMatches() {
        assertNull(LanguageId.identify("42 99", listOf(Languages.EN, Languages.FR)))
    }

    @Test
    fun commitRecordsTurnAndAdvances() {
        val e = ConversationEngine(enEs)
        e.start()
        e.directionFor("hello", Languages.EN)
        val turn = e.commit(TranslationResult("hello", "hola", enEs))
        assertEquals("hola", turn.spoken)
        assertEquals(ConvStatus.SPEAKING, e.status)
        e.ready()
        assertEquals(ConvStatus.LISTENING, e.status)
        assertEquals(1, e.turns.size)
    }

    @Test
    fun deviceTierSelection() {
        assertEquals(DeviceTier.LOW, tierFor(4 * GB, hasNpu = false))
        assertEquals(DeviceTier.MID, tierFor(8 * GB, hasNpu = false))
        assertEquals(DeviceTier.FLAGSHIP, tierFor(16 * GB, hasNpu = true))
        // 16GB but no NPU is still only MID: the NPU is what unlocks the big model.
        assertEquals(DeviceTier.MID, tierFor(16 * GB, hasNpu = false))
    }

    @Test
    fun catalogPicksBestPackThatFits() {
        val lowMt = ModelCatalog.forStage(Stage.MT, DeviceTier.LOW)
        assertNotNull(lowMt)
        assertTrue(lowMt!!.minTier.ordinal <= DeviceTier.LOW.ordinal)

        // The chosen pack must be one the tier can run.
        val flagshipMt = ModelCatalog.forStage(Stage.MT, DeviceTier.FLAGSHIP)
        assertNotNull(flagshipMt)
        assertTrue(flagshipMt!!.minTier.ordinal <= DeviceTier.FLAGSHIP.ordinal)
    }

    @Test
    fun catalogPrefersAPublishedPackOverAPlannedUpgrade() {
        // A MID device can run the planned Hunyuan/Gemma packs (blank url) and the
        // published Qwen pack; forStage must pick the one that actually has a url so the
        // stage gets a real engine instead of silently falling back to the stub.
        val midMt = ModelCatalog.forStage(Stage.MT, DeviceTier.MID)
        assertNotNull(midMt)
        assertTrue(midMt!!.url.isNotBlank())
    }

    @Test
    fun languageLookup() {
        assertEquals(Languages.JA, Languages.byCode("ja"))
        assertEquals(Languages.JA, Languages.byCode("JA"))
        assertNull(Languages.byCode("xx"))
    }
}
