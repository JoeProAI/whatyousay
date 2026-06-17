"""QLoRA fine-tune of the MT model with Unsloth, sized for a single RTX 5080.

Loads the base in 4-bit, trains LoRA adapters on the travel/conversation data
built by prep_data.py, masks the prompt so loss is only on the translation, and
saves the adapter to outputs/adapter.

Run (inside the Blackwell-ready environment, see README):
    python train_qlora.py
"""

from __future__ import annotations

import os

from config import CFG

# Unsloth must be imported before transformers/trl so its patches apply.
from unsloth import FastModel  # noqa: E402
from unsloth.chat_templates import train_on_responses_only  # noqa: E402

from datasets import load_dataset  # noqa: E402
from trl import SFTConfig, SFTTrainer  # noqa: E402


def main() -> None:
    model, tokenizer = FastModel.from_pretrained(
        model_name=CFG.base_model,
        max_seq_length=CFG.max_seq_length,
        load_in_4bit=CFG.load_in_4bit,
        full_finetuning=False,
    )

    model = FastModel.get_peft_model(
        model,
        r=CFG.lora_r,
        lora_alpha=CFG.lora_alpha,
        lora_dropout=CFG.lora_dropout,
        bias="none",
        target_modules=[
            "q_proj", "k_proj", "v_proj", "o_proj",
            "gate_proj", "up_proj", "down_proj",
        ],
        use_gradient_checkpointing="unsloth",
        random_state=CFG.seed,
    )

    raw = load_dataset(
        "json",
        data_files={"train": CFG.train_file, "validation": CFG.val_file},
    )

    def to_text(batch: dict) -> dict:
        texts = [
            tokenizer.apply_chat_template(
                msgs, tokenize=False, add_generation_prompt=False
            )
            for msgs in batch["messages"]
        ]
        return {"text": texts}

    train_ds = raw["train"].map(to_text, batched=True, remove_columns=raw["train"].column_names)
    val_ds = raw["validation"].map(to_text, batched=True, remove_columns=raw["validation"].column_names)

    trainer = SFTTrainer(
        model=model,
        tokenizer=tokenizer,
        train_dataset=train_ds,
        eval_dataset=val_ds,
        args=SFTConfig(
            dataset_text_field="text",
            max_seq_length=CFG.max_seq_length,
            per_device_train_batch_size=CFG.batch_size,
            gradient_accumulation_steps=CFG.grad_accum,
            warmup_ratio=CFG.warmup_ratio,
            num_train_epochs=CFG.epochs,
            learning_rate=CFG.learning_rate,
            logging_steps=10,
            optim="adamw_8bit",
            weight_decay=0.01,
            lr_scheduler_type="linear",
            seed=CFG.seed,
            output_dir="outputs/checkpoints",
            report_to="none",
            bf16=True,
        ),
    )

    # Gemma 3 chat turn markers: train only on the model's reply.
    trainer = train_on_responses_only(
        trainer,
        instruction_part="<start_of_turn>user\n",
        response_part="<start_of_turn>model\n",
    )

    trainer.train()

    os.makedirs(CFG.adapter_dir, exist_ok=True)
    model.save_pretrained(CFG.adapter_dir)
    tokenizer.save_pretrained(CFG.adapter_dir)
    print(f"\nSaved LoRA adapter to {CFG.adapter_dir}")
    print("Next: python export_gguf.py   (merge + quantize for the app)")


if __name__ == "__main__":
    main()
