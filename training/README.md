# WhatYouSay MT fine-tune pipeline

Fine-tune the on-device machine-translation model with [Unsloth](https://unsloth.ai)
QLoRA, then export a GGUF that runs on the phone via llama.cpp. Training happens
once on a GPU (your RTX 5080); the resulting model runs CPU/NPU-only on the
device, with no GPU and no network in the translation path.

What this produces:

- A LoRA fine-tune of **TranslateGemma 4B** (Gemma 3 architecture, 55 languages),
  adapted to short spoken **travel and conversation** phrases in the app's 16
  languages.
- A quantized **GGUF** (default `q4_k_m`) ready to drop into a model pack.
- A **BLEU + chrF** report (FLORES-200) so the quality gain is measured, not claimed.

## Hardware

Built for a single **NVIDIA RTX 5080** (16 GB, Blackwell, compute capability
`sm_120`). 16 GB comfortably fits a 4B QLoRA run. The catch: Blackwell needs the
**CUDA 12.8 (cu128)** toolchain. An ordinary `pip install torch` pulls a cu126
wheel that cannot see the card and fails with `no kernel image available for
execution on the device`.

## Setup

Pick one of the two paths. Docker is the least painful.

### Option A - Unsloth Docker image (recommended)

Unsloth's official image already has the Blackwell-ready torch/xformers/triton
stack baked in.

```bash
docker run -it --gpus all \
  -v "$PWD":/workspace -w /workspace/training \
  unsloth/unsloth bash
# then, inside the container:
pip install -r requirements.txt
```

### Option B - local venv with cu128 (uv)

From the official Unsloth Blackwell guide
(https://unsloth.ai/docs/blog/fine-tuning-llms-with-blackwell-rtx-50-series-and-unsloth):

```bash
curl -LsSf https://astral.sh/uv/install.sh | sh && source $HOME/.local/bin/env
uv venv .venv --python=3.12 --seed && source .venv/bin/activate

# 1) torch from the cu128 index (this is the part that matters for the 5080)
uv pip install -U vllm --torch-backend=cu128
# 2) unsloth + deps
uv pip install unsloth unsloth_zoo bitsandbytes
# 3) xformers built for Blackwell (wheels may lag; build from source)
uv pip uninstall xformers || true
git clone --depth=1 https://github.com/facebookresearch/xformers --recursive
cd xformers && TORCH_CUDA_ARCH_LIST="12.0" python setup.py install && cd ..
# 4) triton + the rest
uv pip install -U "triton>=3.3.1"
uv pip install -r requirements.txt
```

Sanity check the card is visible before training:

```bash
python -c "import torch; print(torch.cuda.get_device_name(0), torch.cuda.is_available())"
# expect: NVIDIA GeForce RTX 5080 True
```

### Hugging Face access

TranslateGemma and Gemma are gated. Accept the license on the model page, then:

```bash
huggingface-cli login   # paste a token with read access
```

## Run order

```bash
python prep_data.py     # build data/train.jsonl + data/val.jsonl (Tatoeba)
python train_qlora.py   # QLoRA fine-tune -> outputs/adapter
python export_gguf.py   # merge + quantize -> outputs/merged, outputs/gguf
python eval.py          # BLEU/chrF on FLORES (run on base and on outputs/merged)
```

Measuring the gain:

```bash
MODEL_PATH=google/translategemma-4b-it python eval.py   # baseline
MODEL_PATH=outputs/merged                python eval.py   # fine-tuned
```

Everything is configurable through environment variables read by `config.py`
(base model, languages sampled, LoRA rank, batch size, quant type, eval pairs).
To start from plain Gemma instead of TranslateGemma:
`BASE_MODEL=unsloth/gemma-3-4b-it python train_qlora.py`.

## Wiring the GGUF into the app

The catalog entry already exists in
`app/src/main/java/ai/whatyousay/engine/ModelManifest.kt`:

```kotlin
ModelPack("mt-translategemma-4b", Stage.MT, "TranslateGemma 4B (55 languages)",
          2_700 * MB, CORE_LANGS, "Q4_K_M", DeviceTier.FLAGSHIP, "", "")
```

To ship your fine-tune: upload `outputs/gguf/*.gguf` to the model-pack CDN, then
fill that pack's empty `url` and `sha256` (compute with `sha256sum the.gguf`).
`LlamaTranslator` (`engine/LlamaBridge.kt`) loads it through the llama.cpp JNI
bridge. No app code changes are needed beyond the catalog url/sha256.

## Quality expectations (honest)

- High-resource pairs (EN<->ES/FR/DE/IT/PT/ZH/JA/RU): close to cloud quality for
  everyday speech.
- Lower-resource of the set (KO/AR/TR/HI/VI/TH/UK): good but more variable.
- `q4_k_m` quantization costs a little quality versus full precision; Unsloth
  dynamic quants narrow the gap. Set `GGUF_QUANT` to try alternatives.
- The fine-tune mainly improves register and domain consistency on short spoken
  phrases. It is not expected to move generic-benchmark BLEU by a large margin;
  `eval.py` tells you the real delta per language.

## Licenses (clear before selling)

- **TranslateGemma / Gemma**: Gemma Terms of Use. Commercial use and fine-tuned
  derivatives are allowed, but you must ship a Notice file referencing
  ai.google.dev/gemma/terms and pass the prohibited-use restrictions through your
  own terms of service.
- **Tatoeba** (training data): CC-BY 2.0 FR. Commercial use is fine with
  attribution to Tatoeba and its contributors.
- **FLORES-200** (eval only): CC-BY-SA 4.0. Used as a benchmark, not shipped.
- Do **not** base a paid model on NLLB-200: its weights are CC-BY-NC
  (non-commercial), and a fine-tune of it stays non-commercial.
