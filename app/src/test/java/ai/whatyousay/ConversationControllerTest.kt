package ai.whatyousay

import ai.whatyousay.core.ConvStatus
import ai.whatyousay.core.LanguagePair
import ai.whatyousay.core.Languages
import ai.whatyousay.engine.ConversationController
import ai.whatyousay.engine.EngineReadiness
import ai.whatyousay.engine.Speaker
import ai.whatyousay.engine.buildStubPipeline
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class RecordingSpeaker : Speaker {
    var calls = 0
    var lastSamples = 0
    override suspend fun speak(pcm: ShortArray, sampleRate: Int) {
        calls++
        lastSamples = pcm.size
    }
}

class ConversationControllerTest {

    private val enEs = LanguagePair(Languages.EN, Languages.ES)

    private fun controller(speaker: Speaker = RecordingSpeaker()) =
        ConversationController(
            pipeline = buildStubPipeline(),
            initialPair = enEs,
            speaker = speaker,
            readiness = EngineReadiness(mt = false, stt = false, tts = false),
            clock = { 0L },
        )

    @Test
    fun typedTextRunsTheTurnAndSpeaks() = runTest {
        val speaker = RecordingSpeaker()
        val c = controller(speaker)
        c.startListening()

        c.submitText("hello")

        val state = c.state.value
        assertEquals(1, state.turns.size)
        assertEquals("hello", state.turns.first().heard)
        assertEquals("hola", state.turns.first().spoken)
        assertEquals("", state.partial)
        assertNull(state.error)
        // The stub synthesizer returns non-empty PCM, so playback was invoked once.
        assertEquals(1, speaker.calls)
        assertTrue(speaker.lastSamples > 0)
        // After synthesis the engine returns to listening (the session was started).
        assertEquals(ConvStatus.LISTENING, state.status)
    }

    @Test
    fun blankTextProducesNoTurn() = runTest {
        val c = controller()
        c.submitText("   ")
        assertTrue(c.state.value.turns.isEmpty())
    }

    @Test
    fun directionFlipsWhenTargetLanguageIsHeard() = runTest {
        val c = controller()
        // The stub transcriber tags audio with the hint language, so a target-side
        // hint makes the controller translate back toward the source.
        c.submitAudio(ShortArray(16_000), hint = Languages.ES)
        val turn = c.state.value.turns.single()
        assertEquals(Languages.ES, turn.source)
        assertEquals(Languages.EN, turn.target)
    }

    @Test
    fun setPairAndSwapUpdateState() = runTest {
        val c = controller()
        c.setPair(LanguagePair(Languages.EN, Languages.FR))
        assertEquals(Languages.FR, c.state.value.pair.target)
        c.swap()
        assertEquals(Languages.FR, c.state.value.pair.source)
        assertEquals(Languages.EN, c.state.value.pair.target)
    }

    @Test
    fun clearErrorResetsErrorField() = runTest {
        val c = controller()
        c.submitText("hello")
        c.clearError()
        assertNull(c.state.value.error)
    }

    @Test
    fun handsFreeFlagTracksToggle() = runTest {
        val c = controller()
        assertFalse(c.state.value.handsFree)
        c.setHandsFree(true)
        assertTrue(c.state.value.handsFree)
        c.setHandsFree(false)
        assertFalse(c.state.value.handsFree)
    }
}
