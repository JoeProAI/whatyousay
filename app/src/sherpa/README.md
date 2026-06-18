# sherpa-onnx voice source set (gated)

This source set holds the on-device voice engines (Whisper STT, Silero VAD, Kokoro/Piper
TTS) implemented against [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx). It is
**compiled only** when the app is built with `-PwithNative=true`. The default build and
CI never compile it, so the app keeps building and running on the stub pipeline with no
NDK and no AAR.

## Pinned version

sherpa-onnx **v1.13.3**. The Kotlin classes used here (`OfflineRecognizer`, `OfflineTts`,
`Vad`, and their config types) match that release's `kotlin-api` exactly. Pin the AAR you
vendor to the same tag.

## Vendoring the AAR

sherpa-onnx for Android is not on Maven Central; it is distributed as a prebuilt AAR via
GitHub releases. To produce `app/libs/sherpa-onnx.aar`:

1. Download the Android archive for the pinned release:
   `https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.3/sherpa-onnx-v1.13.3-android.tar.bz2`
   (or build it from source with `./build-android-arm64-v8a.sh`).
2. Build the AAR:
   ```
   cd sherpa-onnx
   ./gradlew :sherpa_onnx:assembleRelease
   ```
3. Copy the resulting `.aar` to `app/libs/sherpa-onnx.aar`.

The AAR is a large binary and is **not** committed to the repo (`app/libs/*.aar` is
gitignored). Build it locally or pull it in CI from your own artifact store.

## Build with native engines

```
./gradlew :app:assembleDebug -PwithNative=true -PllamaCppDir=/path/to/llama.cpp
```

`withNative` enables three things at once: the llama.cpp CMake build (MT), this source
set, and the vendored AAR dependency. Without it, none are present.

## Expected model-pack directory layout

`SherpaVoiceFactory` reads models from an extracted pack directory (downloaded and
sha256-verified by `FileModelManager`), not from APK assets. Conventions:

- **Whisper STT pack**: a `*encoder*.onnx`, a `*decoder*.onnx`, and `tokens.txt`.
- **Kokoro TTS pack**: a `*.onnx` model, `voices.bin`, `tokens.txt`, and an
  `espeak-ng-data/` directory. Presence of `voices.bin` selects the Kokoro path.
- **Piper TTS pack**: a `*.onnx` VITS model, `tokens.txt`, and an `espeak-ng-data/`
  directory (no `voices.bin`).
- **Silero VAD**: a single `silero_vad.onnx` file.

All paths are app-private files; nothing here touches the network.
