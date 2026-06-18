// JNI bridge for the on-device machine-translation engine.
//
// Implements the native methods declared in
// ai.whatyousay.engine.LlamaTranslator. The model is a GGUF translation model
// (TranslateGemma 4B, fine-tuned by the training/ pipeline) run on the phone CPU
// through llama.cpp. The prompt format mirrors training/config.py exactly so
// inference matches the fine-tune.
//
// Symbol naming: nativeInit is a static native on LlamaTranslator (hoisted from
// the companion by @JvmStatic), so it receives jclass. nativeTranslate and
// nativeFree are instance natives and receive jobject.

#include <jni.h>
#include <android/log.h>

#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "whatyousay_llama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

// One loaded model plus its inference context. The mutex serializes decode calls
// so a single handle is safe to share across coroutines.
struct TranslatorContext {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    const llama_vocab* vocab = nullptr;
    int n_threads = 4;
    std::mutex mutex;
};

std::once_flag g_backend_once;

std::string to_std_string(JNIEnv* env, jstring value) {
    if (value == nullptr) {
        return {};
    }
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string out = chars != nullptr ? chars : "";
    if (chars != nullptr) {
        env->ReleaseStringUTFChars(value, chars);
    }
    return out;
}

// Mirror of training/config.py LANGS. Unknown codes fall through to the code
// itself, which keeps the prompt well-formed for languages added later.
std::string lang_name(const std::string& code) {
    static const struct {
        const char* code;
        const char* name;
    } kLangs[] = {
        {"en", "English"},   {"es", "Spanish"},   {"fr", "French"},
        {"de", "German"},    {"it", "Italian"},   {"pt", "Portuguese"},
        {"zh", "Chinese"},   {"ja", "Japanese"},  {"ko", "Korean"},
        {"ar", "Arabic"},    {"ru", "Russian"},   {"hi", "Hindi"},
        {"tr", "Turkish"},   {"vi", "Vietnamese"},{"th", "Thai"},
        {"uk", "Ukrainian"},
    };
    for (const auto& entry : kLangs) {
        if (code == entry.code) {
            return entry.name;
        }
    }
    return code;
}

// Build the chat-templated prompt. Uses the GGUF's own chat template when present
// (Gemma), falling back to explicit turn markers that match the fine-tune.
std::string build_prompt(llama_model* model, const std::string& instruction) {
    const char* tmpl = llama_model_chat_template(model, nullptr);
    if (tmpl != nullptr) {
        llama_chat_message message{"user", instruction.c_str()};
        std::vector<char> buf(instruction.size() + 512);
        int32_t n = llama_chat_apply_template(
            tmpl, &message, 1, /*add_ass=*/true, buf.data(), static_cast<int32_t>(buf.size()));
        if (n > static_cast<int32_t>(buf.size())) {
            buf.resize(n);
            n = llama_chat_apply_template(
                tmpl, &message, 1, true, buf.data(), static_cast<int32_t>(buf.size()));
        }
        if (n > 0) {
            return std::string(buf.data(), n);
        }
    }
    return "<start_of_turn>user\n" + instruction + "<end_of_turn>\n<start_of_turn>model\n";
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_ai_whatyousay_engine_LlamaTranslator_nativeInit(
    JNIEnv* env, jclass /*clazz*/, jstring model_path, jint threads) {
    std::call_once(g_backend_once, []() { llama_backend_init(); });

    const std::string path = to_std_string(env, model_path);

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;  // phones run on CPU
    llama_model* model = llama_model_load_from_file(path.c_str(), model_params);
    if (model == nullptr) {
        LOGE("failed to load model: %s", path.c_str());
        return 0;
    }

    const int n_threads = threads > 0 ? threads : 4;
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 1024;
    ctx_params.n_batch = 1024;
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;
    llama_context* ctx = llama_init_from_model(model, ctx_params);
    if (ctx == nullptr) {
        LOGE("failed to create context");
        llama_model_free(model);
        return 0;
    }

    auto* translator = new TranslatorContext();
    translator->model = model;
    translator->ctx = ctx;
    translator->vocab = llama_model_get_vocab(model);
    translator->n_threads = n_threads;
    LOGI("loaded translation model (threads=%d)", n_threads);
    return reinterpret_cast<jlong>(translator);
}

extern "C" JNIEXPORT jstring JNICALL
Java_ai_whatyousay_engine_LlamaTranslator_nativeTranslate(
    JNIEnv* env, jobject /*thiz*/, jlong handle, jstring text_, jstring src_, jstring tgt_) {
    auto* translator = reinterpret_cast<TranslatorContext*>(handle);
    if (translator == nullptr) {
        return env->NewStringUTF("");
    }
    std::lock_guard<std::mutex> lock(translator->mutex);

    const std::string text = to_std_string(env, text_);
    const std::string src = to_std_string(env, src_);
    const std::string tgt = to_std_string(env, tgt_);

    const std::string instruction =
        "Translate the following from " + lang_name(src) + " to " + lang_name(tgt) + ":\n" + text;
    const std::string prompt = build_prompt(translator->model, instruction);

    const int32_t n_prompt = -llama_tokenize(
        translator->vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
        nullptr, 0, /*add_special=*/true, /*parse_special=*/true);
    if (n_prompt <= 0) {
        return env->NewStringUTF("");
    }
    std::vector<llama_token> tokens(n_prompt);
    if (llama_tokenize(
            translator->vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
            tokens.data(), static_cast<int32_t>(tokens.size()), true, true) < 0) {
        return env->NewStringUTF("");
    }

    // Fresh state for every translation: each call is independent.
    llama_memory_clear(llama_get_memory(translator->ctx), true);

    // Greedy decoding keeps translation deterministic.
    llama_sampler* sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    std::string result;
    const int32_t n_ctx = static_cast<int32_t>(llama_n_ctx(translator->ctx));
    const int max_new_tokens = 256;

    llama_batch batch = llama_batch_get_one(tokens.data(), static_cast<int32_t>(tokens.size()));
    llama_token new_token_id = 0;
    int32_t n_used = static_cast<int32_t>(tokens.size());

    for (int generated = 0; generated < max_new_tokens; ++generated) {
        if (n_used + 1 > n_ctx) {
            break;
        }
        if (llama_decode(translator->ctx, batch) != 0) {
            LOGE("llama_decode failed");
            break;
        }
        new_token_id = llama_sampler_sample(sampler, translator->ctx, -1);
        if (llama_vocab_is_eog(translator->vocab, new_token_id)) {
            break;
        }
        char piece[256];
        const int32_t n_piece = llama_token_to_piece(
            translator->vocab, new_token_id, piece, sizeof(piece), 0, /*special=*/false);
        if (n_piece > 0) {
            result.append(piece, n_piece);
        }
        batch = llama_batch_get_one(&new_token_id, 1);
        n_used += 1;
    }

    llama_sampler_free(sampler);

    // Trim leading whitespace the template can introduce before the model turn.
    size_t start = result.find_first_not_of(" \n\r\t");
    if (start == std::string::npos) {
        return env->NewStringUTF("");
    }
    return env->NewStringUTF(result.substr(start).c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_ai_whatyousay_engine_LlamaTranslator_nativeFree(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    auto* translator = reinterpret_cast<TranslatorContext*>(handle);
    if (translator == nullptr) {
        return;
    }
    if (translator->ctx != nullptr) {
        llama_free(translator->ctx);
    }
    if (translator->model != nullptr) {
        llama_model_free(translator->model);
    }
    delete translator;
}
