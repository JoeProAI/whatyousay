package ai.whatyousay.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/**
 * Plays 16-bit mono PCM through a streaming AudioTrack and blocks until playback
 * finishes, so the caller can return to listening only once speech has been spoken.
 * On-device only; never touches the network.
 */
class AudioPlayer {

    fun play(samples: ShortArray, sampleRate: Int) {
        if (samples.isEmpty()) return
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, CHANNEL, ENCODING)
        val track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(CHANNEL)
                .setEncoding(ENCODING)
                .build(),
            maxOf(minBuf, samples.size * 2),
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
        try {
            track.play()
            var offset = 0
            while (offset < samples.size) {
                val written = track.write(samples, offset, samples.size - offset)
                if (written <= 0) break
                offset += written
            }
            while (track.playState == AudioTrack.PLAYSTATE_PLAYING &&
                track.playbackHeadPosition < samples.size
            ) {
                Thread.sleep(10)
            }
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    private companion object {
        const val CHANNEL = AudioFormat.CHANNEL_OUT_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }
}
