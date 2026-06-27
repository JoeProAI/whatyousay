# Model packs

WhatYouSay ships no model weights inside the APK. Each stage (MT, STT, TTS) downloads a
pack over wifi on first use, verifies it against a pinned sha256, and stores it in
app-private storage. After that the translation path is fully offline; the only network
use in the whole app is this user-initiated, hash-verified download.

The catalog lives in
[`ModelManifest.kt`](app/src/main/java/ai/whatyousay/engine/ModelManifest.kt). Packs with
a non-blank `url`/`sha256` are real and downloadable today; the rest are the planned
higher-tier upgrades, whose `url` is filled in as each pack is published. When a selected
pack has no `url`, the download is a no-op and that stage falls back to its stub, so the
app degrades gracefully instead of failing hard.

## Shipping packs (`models-v1`)

Released at
`https://github.com/JoeProAI/whatyousay/releases/download/models-v1`.

| Stage | id | Asset | sha256 |
| --- | --- | --- | --- |
| MT | `mt-qwen25-05b` | `qwen2.5-0.5b-instruct-q4_k_m.gguf` | `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db` |
| STT | `stt-whisper-tiny` | `stt-whisper-tiny.zip` | `3c2c96209775185bfec00b0d4d3eab8c80bbc41d604ce6ad9831ed760c39f252` |
| TTS | `tts-piper` | `tts-piper-amy.zip` | `1950bc7f911773a1b91c25ebd9a0d5ed87bcb56f26ec31e668104a82160866d5` |
| VAD | (installed under `models/vad/`) | `silero_vad.onnx` | `9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6` |

MT note: the SPEC names Hunyuan Hy-MT2 as the eventual primary translator. The first
shipping MT pack is **Qwen2.5-0.5B-Instruct (Q4_K_M)** because it is small, permissively
licensed, multilingual, and runs comfortably on a low-tier phone. The native bridge
([`llama_jni.cpp`](app/src/main/cpp/llama_jni.cpp)) applies the GGUF's own chat template,
so swapping in Hy-MT2 / TranslateGemma later is a catalog change only, no native change.

## Pack format

- **MT** is a single GGUF, stored as-is.
- **STT/TTS** are multi-file sherpa-onnx models (encoder/decoder ONNX + tokens +
  `espeak-ng-data/`), so they ship as `.zip`. The manager extracts them after
  verification, flattening a single top-level directory so the engines find their files
  directly. `.zip` (and `.tar.gz`) are used because Android has no native bzip2;
  extraction is dependency-free
  ([`ArchiveExtractor.kt`](app/src/main/java/ai/whatyousay/engine/ArchiveExtractor.kt)).

### Producing the assets

```bash
# STT: repack the official sherpa-onnx whisper-tiny bundle as a zip
#   (https://github.com/k2-fsa/sherpa-onnx/releases, sherpa-onnx-whisper-tiny.tar.bz2)
tar xf sherpa-onnx-whisper-tiny.tar.bz2
(cd sherpa-onnx-whisper-tiny && rm -rf test_wavs) # weights only
zip -r stt-whisper-tiny.zip sherpa-onnx-whisper-tiny

# TTS: repack the official Piper en_US amy (low) vits bundle as a zip
#   (vits-piper-en_US-amy-low.tar.bz2)
tar xf vits-piper-en_US-amy-low.tar.bz2
zip -r tts-piper-amy.zip vits-piper-en_US-amy-low

# MT: Qwen2.5-0.5B-Instruct Q4_K_M GGUF, downloaded as-is from its GGUF release.
sha256sum *.zip *.gguf silero_vad.onnx   # must match the table above
```

## Verifying end to end on a device/emulator

The instrumented test
[`Phase1PipelineTest`](app/src/androidTest/java/ai/whatyousay/Phase1PipelineTest.kt)
extracts the packs through the real `FileModelManager`, builds the pipeline through the
same `PipelineFactory` the app uses, and runs one STT -> MT -> TTS turn on the real
engines. It self-skips on a stub build.

```bash
# 1. Build the native app + test APKs (x86_64 emulator shown; use arm64-v8a for a phone)
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest \
  -PwithNative=true -PnativeAbis=x86_64

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

# 2. Stage the model assets where the test expects them (the app's files/staged dir)
adb root
PKG=ai.whatyousay; D=/data/data/$PKG/files/staged
adb shell mkdir -p $D
for f in stt-whisper-tiny.zip tts-piper-amy.zip \
         qwen2.5-0.5b-instruct-q4_k_m.gguf silero_vad.onnx 0.wav; do
  adb push $f /data/local/tmp/$f && adb shell cp /data/local/tmp/$f $D/$f
done
adb shell chmod -R 777 $D   # 0.wav is any 16 kHz mono 16-bit PCM clip

# 3. Run it
adb shell am instrument -w \
  -e class ai.whatyousay.Phase1PipelineTest \
  ai.whatyousay.test/androidx.test.runner.AndroidJUnitRunner
```

Observed on an x86_64 emulator (CPU, swiftshader):

```
STT => 'After early nightfall, the yellow lamps would light up here and there
        the squalid quarter of the brothels.' (lang=en)
MT  => 'Hola, ¿cómo estás hoy?'
TTS => 32512 samples
```
