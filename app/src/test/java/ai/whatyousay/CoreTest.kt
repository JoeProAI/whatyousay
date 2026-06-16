package ai.whatyousay

import ai.whatyousay.core.ConvStatus
import ai.whatyousay.core.ConversationEngine
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
        assertTrue(lowMt!!.minTier == DeviceTier.LOW)

        val flagshipMt = ModelCatalog.forStage(Stage.MT, DeviceTier.FLAGSHIP)
        assertEquals(DeviceTier.FLAGSHIP, flagshipMt!!.minTier)
    }

    @Test
    fun languageLookup() {
        assertEquals(Languages.JA, Languages.byCode("ja"))
        assertEquals(Languages.JA, Languages.byCode("JA"))
        assertNull(Languages.byCode("xx"))
    }
}
