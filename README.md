# What You Say

**whatyousay.ai** — the translator that works when nothing else does.

Fully on-device voice and text translation. Zero internet, zero data leaving the phone. Built for the dead zone: travel, border crossings, subways, field work, anywhere with no signal and no reason to trust a cloud with the conversation.

See [`SPEC.md`](./SPEC.md) for the full product and technical design.

## The wedge

Google and Apple ship offline translation, but it is text-first, limited, closed, and still phones home. What You Say runs the entire pipeline (speech in, translation, speech out) locally, and proves it by working in airplane mode. The name is the product: "what you say" is what it translates, "what'd you say?" is when you reach for it.

## The on-device stack

| Stage | Model | Runtime |
|-------|-------|---------|
| Translate | Tencent Hunyuan Hy-MT2 (~440MB, 33 langs), HY-MT1.5-1.8B, TranslateGemma 4B, optional NLLB-200 | llama.cpp (GGUF) |
| Speech to text | whisper small / base / tiny + VAD | whisper.cpp |
| Speech to speech | Piper, Kokoro-82M on flagships | sherpa-onnx |
| Camera | MiniCPM-V (signs, menus) | llama.cpp / ExecuTorch |

NPU acceleration on Snapdragon and MediaTek flagships via ExecuTorch (Qualcomm QNN / MediaTek backends).

## Module map

```
core/      pure Kotlin domain: Language, pipeline interfaces, ConversationEngine.
           No Android, no JNI, fully unit-tested on the JVM.
engine/    model catalog + manager, JNI bridges (llama.cpp, whisper, Piper),
           the foreground conversation service, and a runnable stub pipeline.
design/    token-driven theme: signal-orange accent, 1px structure, no AI-trope look.
feature/   Compose UI. Conversation mode first.
```

## Build

Open in Android Studio (it is a standard Gradle project) and run, or:

```bash
./gradlew :app:assembleDebug   # build the APK
./gradlew :app:testDebugUnitTest   # run the core unit tests
```

Phase 0 ships with a local **stub pipeline**, so the app builds and runs today with no model downloads and no native libraries. Type a phrase and watch the conversation flow work end to end. The mic loop and native models drop in behind the same interfaces in Phase 1.

## Roadmap

- **Phase 0 (here):** buildable project, design system, pure-Kotlin domain + ConversationEngine, engine interfaces and JNI bridges, model catalog, stub pipeline, conversation UI.
- **Phase 1:** sherpa-onnx STT/TTS/VAD loop, llama.cpp MT, conversation mode end to end on a mid-tier device.
- **Phase 2:** camera translate (MiniCPM-V), the learning phrasebook, ExecuTorch NPU path.
- **Phase 3:** overheard mode, GlassBrain / Limitless ambient integration, optional Carapace-gated phrasebook sync through clawd.run.

## Enabling native engines (Phase 1)

The JNI bridges in `engine/` (`LlamaTranslator`, `WhisperTranscriber`, `PiperSynthesizer`) declare the native contract. To wire them, add an `externalNativeBuild` CMake block to `app/build.gradle.kts`, vendor the llama.cpp and whisper.cpp sources under `app/src/main/cpp`, and build `libwhatyousay_llama.so` and `libwhatyousay_voice.so`. Until then, the stub pipeline is the default and nothing calls `System.loadLibrary`.

MIT. Built by JoeProAI.
