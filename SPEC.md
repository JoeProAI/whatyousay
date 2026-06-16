# What You Say

**whatyousay.ai** — the translator that works when nothing else does.

Fully on-device. Voice and text. Zero internet, zero data leaving the phone. Built for the dead zone.

Version 0.1 spec. Last updated 2026-05-29. Native Android.

---

## The one-paragraph version

Every translation app worth using assumes a network. Google and Apple ship offline packs, but they are text-first, limited, closed, and they still phone home. What You Say is the opposite bet: the entire pipeline (speech in, translation, speech out) runs locally on the phone, and the product proves it by working in airplane mode. The name is the feature. "What you say" is the thing it translates, and "what'd you say?" is the exact moment you reach for it: standing in front of someone whose language you do not speak, in a place with no signal and no reason to trust a cloud with the conversation. Travelers, journalists, aid workers, lawyers, anyone in a border zone or a basement or a country that reads the wire. The wedge is not "AI translation." It is sovereignty: your words, your device, nothing leaves.

---

## Why this is not a Google Translate clone

Offline translation is not new. A genuinely private, voice-native, hackable one is.

- **Google and Apple offline** cover fewer languages offline than online, lean on text, and remain telemetry-bound and closed. You cannot swap the model, you cannot see what leaves, you cannot extend it.
- **What You Say** is local by construction and open by design. The voice loop runs on-device. You can swap the translation model the way OpenClaw swaps LLMs, because the same principle applies: the model is disposable, the experience is the product. And because nothing leaves, the privacy claim is testable, not promised. Flip on airplane mode and it still works. That demo closes the sale.

The bet: a real slice of people cannot or will not send a live conversation to a server. That slice is underserved, and it overlaps exactly with the people who already care about the rest of the stack here.

---

## The on-device pipeline

Three stages, all local. Speech to text, text to text, text to speech. Each stage has a primary model and device-tiered fallbacks, all delivered as downloadable packs the user fetches once over wifi.

| Stage | Primary (mid-tier phone) | Lightweight (low-end) | Quality (flagship) |
|-------|--------------------------|------------------------|--------------------|
| **MT** | Tencent Hunyuan **Hy-MT2**, ~440MB on-device, offline, 33 languages, reported to rival Gemini-class quality | HY-MT1.5-1.8B quantized | TranslateGemma 4B (Gemma 3, 55 languages, can read text in images) |
| **STT** | whisper.cpp **small** (about 95% of large-v3 accuracy at ~6x speed) + VAD | whisper base / tiny | whisper large-v3-turbo |
| **TTS** | **Piper** (lightweight, multilingual, real-time on most devices) | Piper low-quality voices | **Kokoro-82M** (2026 quality bar, human-indistinguishable on capable hardware) |
| **Camera** | MiniCPM-V (multimodal, runs on Snapdragon 8 Gen 3 class) for signs and menus, offline OCR plus translate | text-OCR fallback (ML Kit on-device) | MiniCPM-V full |

Breadth option: bundle **NLLB-200-distilled-600M** as an optional pack for users who need one of the 200 languages the mainline models do not cover. Coverage over polish, user's choice.

This is also the answer to the original notes. "Tracent translater opensource" is Tencent Hunyuan-MT, open-sourced September 2025, with the Hy-MT2 on-device variant being the one that makes a 440MB fully-offline translator actually good. "MiniCBM" is MiniCPM, which earns its place as the camera/multimodal path rather than the core text engine.

---

## Architecture

Native Android, Kotlin, Jetpack Compose. Clean module split so the model layer is swappable and the UI never talks to JNI directly.

```
  app/                Compose UI, navigation, foreground service
   ├─ feature/conversation   two-way diarized live translation
   ├─ feature/text           type or paste, translate, copy
   ├─ feature/camera         point at a sign, read it back
   ├─ feature/phrasebook     your learned phrases, offline
   └─ feature/models         download / pick / verify model packs
  core/               pure Kotlin domain. No Android, no JNI.
   ├─ Language, LanguagePair, TranslationResult
   ├─ Transcriber / Translator / Synthesizer   (interfaces)
   └─ ConversationEngine     turn-taking state machine
  engine/             native bridges + model management
   ├─ LlamaBridge      MT via llama.cpp (GGUF) over JNI
   ├─ SherpaVoice      STT + TTS + VAD via sherpa-onnx (AAR)
   ├─ ExecuTorchAccel  NPU path (Qualcomm QNN / MediaTek) on flagships
   └─ ModelManager     download, hash-verify, tier-select, store
  design/             token-driven design system (theme, type, components)
```

### Runtimes, and why

- **MT on llama.cpp (GGUF).** It is the most flexible local runtime, has the deepest quantization story for squeezing models into a phone memory budget, and integrates cleanly into Kotlin over JNI. Hunyuan and NLLB and Gemma packs all ship as GGUF.
- **Voice on sherpa-onnx.** It bundles whisper STT, Piper and Kokoro TTS, and VAD behind one Android AAR with JNI already done. That collapses the hardest part of the build (the real-time audio loop) into a maintained dependency instead of three separate native integrations.
- **ExecuTorch for NPU.** On Snapdragon and MediaTek flagships, ExecuTorch exposes Qualcomm QNN and MediaTek backends for real NPU offload, which is the difference between warm-battery real-time and a stutter. It is the acceleration path, not the baseline, so low-end devices still work on CPU.

### The audio loop (conversation mode)

A foreground service owns an `AudioRecord` stream. VAD segments speech. Each segment runs STT, then language detection picks the source side, then MT translates to the other side, then TTS speaks it. Partial STT results stream to the screen so the listener sees words forming. The phone sits between two people with a split screen, each half oriented to one speaker in their own language. No taps once it starts. That hands-free, turn-taking flow is the thing Google offline does not do well, and it is most of the daily-use value.

---

## The five things that make it worth installing

1. **Conversation mode.** Two people, two languages, one phone, hands-free. The headline.
2. **Overheard mode.** Live captions of the foreign speech around you. This is the literal "what'd they just say?" feature, and it is the bridge to the rest of Joe's stack: pipe it through a GlassBrain Ray-Ban HUD or a Limitless capture and you get ambient translation without holding anything.
3. **Camera translate.** Point at a menu or a sign, read it back, offline, via MiniCPM-V.
4. **A phrasebook that learns you.** It remembers the phrases you actually use and the corrections you make, and builds a private on-device phrasebook that gets sharper with use. Memory-first, the same philosophy as the rest of the stack, shrunk to fit a phone and kept entirely local. Every learned entry carries provenance, so this is Carapace-compatible from day one if it ever syncs.
5. **Bring your own model.** A GGUF model manager. Swap Hunyuan for NLLB for breadth, or TranslateGemma for quality, the way OpenClaw swaps LLMs. Power-user feature, on-brand, and it future-proofs the app against whatever model wins next quarter.

---

## Device tiering

The app reads RAM and SoC at first launch and picks a default pack so it runs on a $150 phone and sings on a flagship.

| Tier | Heuristic | MT | STT | TTS |
|------|-----------|----|-----|-----|
| Low | < 6GB RAM, no usable NPU | Hy-MT2 440MB | whisper tiny/base | Piper low |
| Mid | 6-12GB RAM | Hy-MT2 / HY-MT1.5-1.8B | whisper small | Piper |
| Flagship | 12GB+ and QNN/MediaTek NPU | TranslateGemma 4B via ExecuTorch | whisper small/turbo | Kokoro-82M |

The user can override. Nothing is locked.

---

## Design system

Following Joe's rules, not the marketplace-template aesthetic.

- Token-driven, not one-off styling. One source of truth for color, type, spacing, motion.
- 1px borders create structure. Backgrounds stay quiet. No purple gradients, no rounded-card-plus-generic-icon look.
- Not Inter, not Roboto, not Arial. A distinct typeface with character (a geometric or humanist sans for UI, a clean mono for the model manager and language codes).
- Every interactive element ships all states: default, hover, active, focus-visible, disabled, loading, error, empty. The conversation screen especially needs strong loading and error states, because STT and MT have real latency and real failure modes (no speech detected, model not downloaded, language unsupported).
- WCAG AA by default. High contrast, large touch targets, full TalkBack support. This is a tool people use in stressful moments, so legibility under pressure matters more than flourish.
- Motion is subtle and purposeful (Compose animation, the spiritual equivalent of the Framer rule). Words fading in as they are transcribed, the speaker indicator sliding between halves. Nothing decorative.
- Zero emoji in the UI or branding.

The brand voice carries the double meaning. The empty state of conversation mode can literally say "what you say?" and mean both things.

---

## Privacy model

This is the product, so it is enforced, not claimed.

- No network permission required for core translation. The app functions with networking fully denied at the OS level.
- The only network use is the model-pack download, which is explicit, user-initiated, and over wifi, with hash verification on every pack.
- Conversations, audio, camera frames, and the learned phrasebook never leave the device. There is no analytics SDK in the translation path.
- "Airplane mode works" is a first-run demo, not fine print.

---

## Model manager

Models ship as signed packs, not baked into the APK (which keeps the install small and the models updatable). The manager downloads a pack, verifies its sha256 against a manifest, stores it in app-private storage, and registers it with the engine. Packs declare their languages, size, quantization, and minimum device tier, so the UI can warn before a user downloads a 4B model onto a 4GB phone. This is the same disposable-model discipline as the OpenClaw architecture, applied to the edge.

---

## Roadmap

**Phase 0 (this scaffold).** Buildable Android project: module structure, Compose design system, the pure-Kotlin domain and `ConversationEngine` state machine, the engine interfaces and JNI bridge signatures, the model manifest and manager interface, and a local stub pipeline so the UI runs and previews before the native models are wired. Opens in Android Studio and runs.

**Phase 1.** Wire sherpa-onnx for the real STT/TTS/VAD loop and llama.cpp for MT. Conversation mode end to end on a mid-tier device. Hy-MT2 and whisper-small packs.

**Phase 2.** Camera translate via MiniCPM-V. The learning phrasebook. ExecuTorch NPU path for flagships.

**Phase 3.** Overheard mode and the GlassBrain / Limitless ambient integration. Optional encrypted sync of the phrasebook through clawd.run, gated by Carapace so even your own translation history cannot be poisoned. The two products meet here.

---

## Naming

What You Say. whatyousay.ai for the site, grab whatyousay.app for the Play Store. Speech-first, warm, and the pun is load-bearing. It tells the user what the app does and when to reach for it in four words.
