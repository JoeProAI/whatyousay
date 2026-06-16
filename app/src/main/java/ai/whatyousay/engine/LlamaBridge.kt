package ai.whatyousay.engine

import ai.whatyousay.core.LanguagePair
import ai.whatyousay.core.Languages
import ai.whatyousay.core.Translator
import ai.whatyousay.core.TranslationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Machine translation via llama.cpp over JNI.
 *
 * The native library libwhatyousay_llama.so is built from llama.cpp by the CMake
 * project in app/src/main/cpp. This Kotlin side owns the model handle lifecycle and
 * marshals strings across the boundary. Inference runs off the main thread.
 */
class LlamaTranslator private constructor(@Volatile private var handle: Long) : Translator {

    override val supported: Set<String> = Languages.all.map { it.code }.toSet()

    override suspend fun translate(text: String, pair: LanguagePair): TranslationResult =
        withContext(Dispatchers.Default) {
            check(handle != 0L) { "LlamaTranslator is closed" }
            val output = nativeTranslate(handle, text, pair.source.code, pair.target.code)
            TranslationResult(sourceText = text, translatedText = output, pair = pair)
        }

    fun close() {
        if (handle != 0L) {
            nativeFree(handle)
            handle = 0L
        }
    }

    private external fun nativeTranslate(handle: Long, text: String, src: String, tgt: String): String
    private external fun nativeFree(handle: Long)

    companion object {
        init {
            System.loadLibrary("whatyousay_llama")
        }

        @JvmStatic
        private external fun nativeInit(modelPath: String, threads: Int): Long

        /** Load a GGUF translation model from disk. Returns null if native init fails. */
        fun load(modelPath: String, threads: Int = 4): LlamaTranslator? {
            val h = nativeInit(modelPath, threads)
            return if (h != 0L) LlamaTranslator(h) else null
        }
    }
}
