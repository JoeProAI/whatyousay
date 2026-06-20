"""Shared configuration for the WhatYouSay MT fine-tune pipeline.

Everything the four scripts (prep_data, train_qlora, export_gguf, eval) need to
agree on lives here so there is a single source of truth. Override any value with
an environment variable of the same name (see `_env`).
"""

from __future__ import annotations

import os
from dataclasses import dataclass, field


def _env(name: str, default: str) -> str:
    return os.environ.get(name, default)


# The 16 languages the app ships with, mirrored from
# app/src/main/java/ai/whatyousay/core/Language.kt (Languages.all).
# ISO 639-1 codes. English is the pivot: we build EN<->X pairs in both directions.
LANGS: dict[str, str] = {
    "en": "English",
    "es": "Spanish",
    "fr": "French",
    "de": "German",
    "it": "Italian",
    "pt": "Portuguese",
    "zh": "Chinese",
    "ja": "Japanese",
    "ko": "Korean",
    "ar": "Arabic",
    "ru": "Russian",
    "hi": "Hindi",
    "tr": "Turkish",
    "vi": "Vietnamese",
    "th": "Thai",
    "uk": "Ukrainian",
}

PIVOT = "en"
# Non-pivot languages we translate to/from English.
TARGETS: list[str] = [c for c in LANGS if c != PIVOT]


@dataclass
class Config:
    # Base checkpoint to fine-tune. TranslateGemma is already translation-tuned
    # (Gemma 3 architecture, 55 languages), so we adapt it to the travel and
    # conversation domain. To start from plain instruction-tuned Gemma instead,
    # set BASE_MODEL=unsloth/gemma-3-4b-it (smaller download, broader support).
    base_model: str = _env("BASE_MODEL", "google/translategemma-4b-it")

    # Sequence length. Spoken phrases are short; 1024 is plenty and keeps VRAM low
    # on a 16 GB card. Raise to 2048 if you add longer documents.
    max_seq_length: int = int(_env("MAX_SEQ_LENGTH", "1024"))

    # QLoRA: load the base in 4-bit, train low-rank adapters on top.
    load_in_4bit: bool = _env("LOAD_IN_4BIT", "1") == "1"
    lora_r: int = int(_env("LORA_R", "16"))
    lora_alpha: int = int(_env("LORA_ALPHA", "16"))
    lora_dropout: float = float(_env("LORA_DROPOUT", "0.0"))

    # Data sampling. Tatoeba pairs per direction (EN->X and X->EN each get this
    # many before dedupe/length filtering). 16 langs * 2 dirs * cap ~= dataset size.
    pairs_per_direction: int = int(_env("PAIRS_PER_DIRECTION", "8000"))
    min_chars: int = int(_env("MIN_CHARS", "2"))
    max_chars: int = int(_env("MAX_CHARS", "200"))
    val_fraction: float = float(_env("VAL_FRACTION", "0.02"))

    # Training hyperparameters tuned for a single 16 GB Blackwell card (RTX 5080).
    # effective batch = batch_size * grad_accum.
    batch_size: int = int(_env("BATCH_SIZE", "2"))
    grad_accum: int = int(_env("GRAD_ACCUM", "8"))
    learning_rate: float = float(_env("LEARNING_RATE", "2e-4"))
    epochs: float = float(_env("EPOCHS", "1"))
    warmup_ratio: float = float(_env("WARMUP_RATIO", "0.03"))
    seed: int = int(_env("SEED", "3407"))

    # Filesystem layout (all gitignored).
    data_dir: str = _env("DATA_DIR", "data")
    train_file: str = _env("TRAIN_FILE", "data/train.jsonl")
    val_file: str = _env("VAL_FILE", "data/val.jsonl")
    adapter_dir: str = _env("ADAPTER_DIR", "outputs/adapter")
    merged_dir: str = _env("MERGED_DIR", "outputs/merged")
    gguf_dir: str = _env("GGUF_DIR", "outputs/gguf")
    gguf_quant: str = _env("GGUF_QUANT", "q4_k_m")

    # Eval
    eval_pairs: list[str] = field(
        default_factory=lambda: [
            p.strip()
            for p in _env("EVAL_PAIRS", "en-es,es-en,en-ja,ja-en,en-de,en-zh").split(",")
            if p.strip()
        ]
    )
    eval_samples: int = int(_env("EVAL_SAMPLES", "200"))


CFG = Config()


def lang_name(code: str) -> str:
    return LANGS.get(code, code)


def translation_prompt(src_code: str, tgt_code: str, text: str) -> str:
    """The instruction we train and infer with. Kept identical everywhere."""
    return f"Translate the following from {lang_name(src_code)} to {lang_name(tgt_code)}:\n{text}"
