# WhatYouSay — Claude Code project context

WhatYouSay is a fully on-device Android voice + text translator (Kotlin, Jetpack Compose). Nothing leaves the phone; it works in airplane mode. Read `SPEC.md` for the full design and `../HANDOFF.md` for the backlog and working agreements. Domain: whatyousay.ai.

## Architecture
- `core/` — pure Kotlin, no Android: Language, pipeline interfaces (Transcriber/Translator/Synthesizer), ConversationEngine state machine. Unit-tested on the JVM.
- `engine/` — ModelManifest (catalog + device tiers), ModelManager, LlamaBridge (llama.cpp JNI), NativeVoice (whisper + Piper JNI), StubPipeline (runs today, no models), ConversationService (foreground mic service).
- `design/Theme.kt` — token-driven theme: signal-orange accent, 1px structure, no AI-trope look, zero emoji.
- `feature/conversation/` — Compose UI.
- `web/index.html` — finished marketing site (deployable single file).

## Model stack (on-device)
Translate: Hunyuan Hy-MT2 (~440MB) primary; HY-MT1.5-1.8B / TranslateGemma / NLLB-200 optional. STT: whisper.cpp small/base/tiny + VAD. TTS: Piper / Kokoro-82M. Runtime: llama.cpp (GGUF) via JNI; ExecuTorch for NPU.

## Verify
```
./gradlew :app:testDebugUnitTest     # core logic (ConversationEngine, model tiers)
./gradlew :app:assembleDebug         # full build (needs Android SDK/NDK)
```

## Working agreements
- The app must build and run on the stub pipeline at all times. Real engines drop in behind the existing interfaces.
- No network in the translation path. The only network use is model-pack download, user-initiated, sha256-verified. The "airplane mode works" claim must stay literally true.
- Check model licenses (Hunyuan, whisper, Piper, NLLB, Gemma) for commercial use + redistribution before monetizing.
- Match the JNI `external` signatures in LlamaBridge.kt and NativeVoice.kt exactly when writing the C++ side. Pin native lib versions.
- No em dashes in prose, zero emoji in UI.

## Blocked on a human
Device build/run and the model files are external. An agent writes the native + Gradle wiring; Joe builds on hardware.
