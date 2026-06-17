"""Build the parallel travel/conversation dataset for the WhatYouSay fine-tune.

Source: Tatoeba sentence pairs (short, spoken, conversational register), pulled
through the Helsinki-NLP/tatoeba_mt dataset on the Hugging Face Hub. Tatoeba is
CC-BY 2.0 FR: commercial use is fine with attribution (see README).

For every non-English language we build EN->X and X->EN examples, filter by
length, dedupe, then write chat-formatted JSONL (train + val). The chat format
matches config.translation_prompt so training and inference never drift.

Run:
    python prep_data.py
Output:
    data/train.jsonl, data/val.jsonl
"""

from __future__ import annotations

import json
import os
import random

from datasets import load_dataset

from config import CFG, PIVOT, TARGETS, lang_name, translation_prompt

random.seed(CFG.seed)

# ISO 639-1 (app codes) -> ISO 639-3, which Tatoeba_mt uses for config + lang fields.
ISO3 = {
    "en": "eng", "es": "spa", "fr": "fra", "de": "deu", "it": "ita",
    "pt": "por", "zh": "cmn", "ja": "jpn", "ko": "kor", "ar": "ara",
    "ru": "rus", "hi": "hin", "tr": "tur", "vi": "vie", "th": "tha",
    "uk": "ukr",
}


def _pair_config(a: str, b: str) -> str:
    """Tatoeba_mt config names are sorted, hyphen-joined ISO codes, e.g. 'eng-spa'."""
    x, y = sorted([ISO3[a], ISO3[b]])
    return f"{x}-{y}"


def _load_pair(src: str, tgt: str) -> list[tuple[str, str]]:
    """Return (src_text, tgt_text) tuples for a directed pair, best-effort."""
    cfg = _pair_config(src, tgt)
    src3, tgt3 = ISO3[src], ISO3[tgt]
    rows: list[tuple[str, str]] = []
    skipped = 0
    for split in ("test", "validation", "train"):
        try:
            ds = load_dataset("Helsinki-NLP/tatoeba_mt", cfg, split=split)
        except Exception as exc:  # noqa: BLE001 - missing split/config is expected
            print(f"  [skip] {cfg} {split}: {exc}")
            continue
        # tatoeba_mt exposes sourceString/targetString with sourceLang/targetLang.
        for row in ds:
            s_lang = (row.get("sourceLang") or "").lower()
            s_text = (row.get("sourceString") or "").strip()
            t_text = (row.get("targetString") or "").strip()
            if not s_text or not t_text:
                continue
            # Orient to (src_lang_text, tgt_lang_text) by matching sourceLang against
            # the known endpoint codes (ISO 639-3 or the 2-letter app code). Do not
            # guess on an unrecognized value: a wrong swap silently degrades training,
            # so skip the row and surface it in the count instead.
            if s_lang.startswith(src3) or s_lang == src:
                rows.append((s_text, t_text))
            elif s_lang.startswith(tgt3) or s_lang == tgt:
                rows.append((t_text, s_text))
            else:
                skipped += 1
        if rows:
            break
    if skipped:
        print(f"  [warn] {cfg}: skipped {skipped} rows with unrecognized sourceLang")
    return rows


def _filter(rows: list[tuple[str, str]]) -> list[tuple[str, str]]:
    seen: set[tuple[str, str]] = set()
    out: list[tuple[str, str]] = []
    for s, t in rows:
        if not (CFG.min_chars <= len(s) <= CFG.max_chars):
            continue
        if not (CFG.min_chars <= len(t) <= CFG.max_chars):
            continue
        key = (s, t)
        if key in seen:
            continue
        seen.add(key)
        out.append((s, t))
    random.shuffle(out)
    return out[: CFG.pairs_per_direction]


def _example(src_code: str, tgt_code: str, src_text: str, tgt_text: str) -> dict:
    return {
        "messages": [
            {"role": "user", "content": translation_prompt(src_code, tgt_code, src_text)},
            {"role": "assistant", "content": tgt_text},
        ],
        "src": src_code,
        "tgt": tgt_code,
    }


def main() -> None:
    os.makedirs(CFG.data_dir, exist_ok=True)
    all_examples: list[dict] = []

    for code in TARGETS:
        print(f"[{code}] {lang_name(code)} <-> {lang_name(PIVOT)}")
        base = _filter(_load_pair(PIVOT, code))
        print(f"  usable pairs: {len(base)}")
        for s_text, t_text in base:
            # EN -> X
            all_examples.append(_example(PIVOT, code, s_text, t_text))
            # X -> EN
            all_examples.append(_example(code, PIVOT, t_text, s_text))

    random.shuffle(all_examples)
    n_val = max(1, int(len(all_examples) * CFG.val_fraction))
    val = all_examples[:n_val]
    train = all_examples[n_val:]

    with open(CFG.train_file, "w", encoding="utf-8") as f:
        for ex in train:
            f.write(json.dumps(ex, ensure_ascii=False) + "\n")
    with open(CFG.val_file, "w", encoding="utf-8") as f:
        for ex in val:
            f.write(json.dumps(ex, ensure_ascii=False) + "\n")

    print(f"\nWrote {len(train)} train / {len(val)} val examples")
    print(f"  {CFG.train_file}\n  {CFG.val_file}")
    if len(train) < 5000:
        print(
            "\nNote: dataset is small. Raise PAIRS_PER_DIRECTION or add more sources "
            "(OPUS, your own travel phrasebook) for a stronger fine-tune."
        )


if __name__ == "__main__":
    main()
