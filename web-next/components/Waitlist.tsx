"use client";

import { useState, type FormEvent } from "react";

type Status = "idle" | "loading" | "done" | "error";

export function Waitlist({ compact = false }: { compact?: boolean }) {
  const [email, setEmail] = useState("");
  const [status, setStatus] = useState<Status>("idle");
  const [message, setMessage] = useState("");

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (status === "loading") return;
    setStatus("loading");
    try {
      const res = await fetch("/api/waitlist", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email }),
      });
      const data = (await res.json()) as { ok: boolean; error?: string };
      if (!res.ok || !data.ok) {
        setStatus("error");
        setMessage(data.error ?? "Something went wrong. Try again.");
        return;
      }
      setStatus("done");
      setMessage("You are on the list. We will email you when the APK drops.");
      setEmail("");
    } catch {
      setStatus("error");
      setMessage("Network error. Try again.");
    }
  }

  if (status === "done") {
    return (
      <div
        className={`flex max-w-[560px] items-center gap-3 border border-signal bg-paper px-4 py-4 ${
          compact ? "" : "backdrop-blur-sm"
        }`}
      >
        <span className="label bg-signal px-2 py-1 font-bold text-white">In</span>
        <span className="text-[0.98rem] text-ink">{message}</span>
      </div>
    );
  }

  return (
    <form onSubmit={onSubmit} className="max-w-[560px]">
      <div className="flex flex-col gap-2.5 sm:flex-row">
        <input
          type="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="you@email.com"
          aria-label="Email address"
          className="font-mono w-full border border-ink bg-paper px-4 py-3.5 text-[0.95rem] text-ink outline-none placeholder:text-inkSoft focus:border-signal focus:ring-2 focus:ring-signal/40"
        />
        <button
          type="submit"
          disabled={status === "loading"}
          className="shrink-0 border border-signal bg-signal px-6 py-3.5 font-semibold text-ink transition-transform hover:-translate-y-0.5 disabled:opacity-60"
        >
          {status === "loading" ? "Adding..." : "Get notified"}
        </button>
      </div>
      <p className="label mt-3 text-inkSoft">
        {status === "error" ? (
          <span className="text-signal">{message}</span>
        ) : (
          "When the build drops / no spam / no trackers"
        )}
      </p>
    </form>
  );
}
