package ai.whatyousay.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Plays synthesized speech. Pulling playback behind a small interface keeps the
 * [ConversationController] free of Android audio types, so the turn loop is unit
 * tested on the JVM with a no-op speaker while the device build plays real PCM.
 */
interface Speaker {
    suspend fun speak(pcm: ShortArray, sampleRate: Int)
}

/** Drops the audio. Used in previews and tests where there is no audio device. */
class NoopSpeaker : Speaker {
    override suspend fun speak(pcm: ShortArray, sampleRate: Int) {}
}

/** Plays 16-bit mono PCM through the device [AudioPlayer] off the main thread. */
class AudioTrackSpeaker(private val player: AudioPlayer = AudioPlayer()) : Speaker {
    override suspend fun speak(pcm: ShortArray, sampleRate: Int) {
        if (pcm.isEmpty()) return
        withContext(Dispatchers.IO) { player.play(pcm, sampleRate) }
    }
}
