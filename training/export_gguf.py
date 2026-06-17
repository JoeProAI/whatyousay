"""Merge the LoRA adapter into the base and export a GGUF for the on-device app.

Produces:
  - outputs/merged   : 16-bit merged safetensors (for eval.py and re-quantizing)
  - outputs/gguf      : quantized GGUF (default q4_k_m) for llama.cpp on the phone

Run:
    python export_gguf.py

If Unsloth's built-in GGUF export struggles with the multimodal TranslateGemma
checkpoint, use the manual llama.cpp fallback printed at the end (it operates on
outputs/merged, which is always produced).
"""

from __future__ import annotations

import os

from config import CFG

from unsloth import FastModel  # noqa: E402


def main() -> None:
    model, tokenizer = FastModel.from_pretrained(
        model_name=CFG.adapter_dir,
        max_seq_length=CFG.max_seq_length,
        load_in_4bit=False,
    )

    # 1) Always produce a 16-bit merge. eval.py reads this, and it is the input
    #    for the manual llama.cpp fallback below.
    os.makedirs(CFG.merged_dir, exist_ok=True)
    model.save_pretrained_merged(CFG.merged_dir, tokenizer, save_method="merged_16bit")
    print(f"Merged 16-bit model -> {CFG.merged_dir}")

    # 2) Try Unsloth's GGUF export (wraps llama.cpp convert + quantize).
    os.makedirs(CFG.gguf_dir, exist_ok=True)
    try:
        model.save_pretrained_gguf(
            CFG.gguf_dir,
            tokenizer,
            quantization_method=CFG.gguf_quant,
        )
        print(f"GGUF ({CFG.gguf_quant}) -> {CFG.gguf_dir}")
        print("\nDrop this .gguf into the app's model pack (see README: wiring to ModelCatalog).")
        return
    except Exception as exc:  # noqa: BLE001
        print(f"\nUnsloth GGUF export failed: {exc}")

    print(
        "\nManual fallback (run from a cloned llama.cpp):\n"
        f"  python llama.cpp/convert_hf_to_gguf.py {CFG.merged_dir} "
        f"--outfile {CFG.gguf_dir}/model-f16.gguf --outtype f16\n"
        f"  ./llama.cpp/build/bin/llama-quantize {CFG.gguf_dir}/model-f16.gguf "
        f"{CFG.gguf_dir}/model-{CFG.gguf_quant}.gguf {CFG.gguf_quant.upper()}\n"
    )


if __name__ == "__main__":
    main()
