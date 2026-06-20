"""Measure translation quality with BLEU and chrF on FLORES-200 devtest.

Scores a Hugging Face model directory (the base, or outputs/merged after
fine-tuning) per language pair so "better" is a number, not an adjective. Run it
twice (base vs merged) and compare, or pass MODEL_PATH to point at either.

FLORES-200 is CC-BY-SA 4.0 and is used here only as an evaluation benchmark; it
is not redistributed and does not touch the shipped model.

Run:
    MODEL_PATH=google/translategemma-4b-it python eval.py      # baseline
    MODEL_PATH=outputs/merged                python eval.py      # fine-tuned
"""

from __future__ import annotations

import os

import sacrebleu
import torch
from datasets import load_dataset
from transformers import AutoModelForCausalLM, AutoTokenizer

from config import CFG, lang_name, translation_prompt

MODEL_PATH = os.environ.get("MODEL_PATH", CFG.merged_dir)

# FLORES-200 uses ISO 639-3 + script codes for its splits.
FLORES_CODE = {
    "en": "eng_Latn", "es": "spa_Latn", "fr": "fra_Latn", "de": "deu_Latn",
    "it": "ita_Latn", "pt": "por_Latn", "zh": "zho_Hans", "ja": "jpn_Jpan",
    "ko": "kor_Hang", "ar": "arb_Arab", "ru": "rus_Cyrl", "hi": "hin_Deva",
    "tr": "tur_Latn", "vi": "vie_Latn", "th": "tha_Thai", "uk": "ukr_Cyrl",
}


def load_flores(code: str) -> list[str]:
    ds = load_dataset("facebook/flores", FLORES_CODE[code], split="devtest")
    return [row["sentence"] for row in ds][: CFG.eval_samples]


def main() -> None:
    print(f"Evaluating: {MODEL_PATH}\n")
    tokenizer = AutoTokenizer.from_pretrained(MODEL_PATH)
    model = AutoModelForCausalLM.from_pretrained(
        MODEL_PATH, torch_dtype=torch.bfloat16, device_map="auto"
    )
    model.eval()

    rows = []
    for pair in CFG.eval_pairs:
        src, tgt = pair.split("-")
        src_sents = load_flores(src)
        ref_sents = load_flores(tgt)

        hyps: list[str] = []
        for text in src_sents:
            msgs = [{"role": "user", "content": translation_prompt(src, tgt, text)}]
            prompt = tokenizer.apply_chat_template(
                msgs, tokenize=False, add_generation_prompt=True
            )
            inputs = tokenizer(prompt, return_tensors="pt").to(model.device)
            with torch.no_grad():
                out = model.generate(
                    **inputs, max_new_tokens=128, do_sample=False, num_beams=1
                )
            gen = tokenizer.decode(
                out[0][inputs["input_ids"].shape[1]:], skip_special_tokens=True
            ).strip()
            hyps.append(gen)

        bleu = sacrebleu.corpus_bleu(hyps, [ref_sents]).score
        chrf = sacrebleu.corpus_chrf(hyps, [ref_sents]).score
        rows.append((pair, bleu, chrf))
        print(f"{pair} ({lang_name(src)}->{lang_name(tgt)})  BLEU {bleu:5.1f}  chrF {chrf:5.1f}")

    print("\n--- summary ---")
    print(f"{'pair':<8}{'BLEU':>8}{'chrF':>8}")
    for pair, bleu, chrf in rows:
        print(f"{pair:<8}{bleu:>8.1f}{chrf:>8.1f}")
    if rows:
        avg_bleu = sum(r[1] for r in rows) / len(rows)
        avg_chrf = sum(r[2] for r in rows) / len(rows)
        print(f"{'avg':<8}{avg_bleu:>8.1f}{avg_chrf:>8.1f}")


if __name__ == "__main__":
    main()
