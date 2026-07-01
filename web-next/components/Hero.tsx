"use client";

import { useEffect, useRef, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { motion, useScroll, useTransform } from "framer-motion";

type Phrase = {
  code: string;
  tag: string;
  dir: "ltr" | "rtl";
  text: string;
};

const PHRASES: Phrase[] = [
  { code: "ES", tag: "EN -> ES", dir: "ltr", text: "¿Dónde está la estación?" },
  { code: "FR", tag: "EN -> FR", dir: "ltr", text: "Où est la gare ?" },
  { code: "DE", tag: "EN -> DE", dir: "ltr", text: "Wo ist der Bahnhof?" },
  { code: "JA", tag: "EN -> JA", dir: "ltr", text: "駅はどこですか？" },
  { code: "ZH", tag: "EN -> ZH", dir: "ltr", text: "车站在哪里？" },
  { code: "AR", tag: "EN -> AR", dir: "rtl", text: "أين المحطة؟" },
  { code: "RU", tag: "EN -> RU", dir: "ltr", text: "Где вокзал?" },
];

export function Hero() {
  const [idx, setIdx] = useState(0);
  const [offline, setOffline] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const { scrollYProgress } = useScroll({
    target: ref,
    offset: ["start start", "end start"],
  });
  const imgY = useTransform(scrollYProgress, [0, 1], ["0%", "18%"]);
  const imgScale = useTransform(scrollYProgress, [0, 1], [1.04, 1.16]);

  useEffect(() => {
    const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (reduce) return;
    const t = setInterval(() => setIdx((i) => (i + 1) % PHRASES.length), 2600);
    return () => clearInterval(t);
  }, []);

  const p = PHRASES[idx];

  return (
    <section ref={ref} className="relative min-h-[100svh] overflow-hidden bg-ink">
      <motion.div
        style={{ y: imgY, scale: imgScale }}
        className="absolute inset-0 z-0"
      >
        <Image
          src="/renders/hero.webp"
          alt="A traveler in a signal-dead valley, their phone fanning spoken words into translated glyphs"
          fill
          priority
          sizes="100vw"
          className="object-cover"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-paper via-paper/10 to-transparent" />
        <div className="absolute inset-0 bg-gradient-to-r from-paper/70 via-transparent to-transparent" />
      </motion.div>

      <div className="relative z-10 flex min-h-[100svh] flex-col justify-end">
        <div className="shell pb-[clamp(40px,7vw,90px)] pt-[clamp(90px,12vw,150px)]">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, ease: [0.22, 1, 0.36, 1] }}
            className="label flex flex-wrap items-center gap-3 text-ink"
          >
            <span>On-device</span>
            <span className="text-signal">/</span>
            <span>Voice + Text</span>
            <span className="text-signal">/</span>
            <span className="font-bold">No signal required</span>
          </motion.div>

          <motion.h1
            initial={{ opacity: 0, y: 26 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.85, delay: 0.08, ease: [0.22, 1, 0.36, 1] }}
            className="mt-6 max-w-[15ch] text-[clamp(2.7rem,8.6vw,6.4rem)] font-bold leading-[0.93] tracking-[-0.035em]"
          >
            Translation that works in the <span className="text-signal">deadzone</span>.
          </motion.h1>

          <motion.div
            initial={{ opacity: 0, y: 26 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.85, delay: 0.16, ease: [0.22, 1, 0.36, 1] }}
            className="mt-9 max-w-[680px] border border-ink/70 bg-paper/85 backdrop-blur-sm"
          >
            <div className="label flex items-center justify-between border-b border-ink/40 px-4 py-2.5 text-inkSoft">
              <span className="flex items-center gap-2">
                <span
                  className={`inline-block h-[7px] w-[7px] rounded-full ${
                    offline ? "bg-inkSoft" : "animate-pulse bg-signal"
                  }`}
                />
                Translating / on device
              </span>
              <span>{p.tag}</span>
            </div>
            <div className="min-h-[128px] px-5 pb-6 pt-5">
              <div className="mb-3.5 text-[1.02rem] text-inkSoft">
                &ldquo;Where is the station?&rdquo;
              </div>
              <motion.div
                key={idx}
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.35 }}
                className="flex items-baseline gap-3.5"
              >
                <span className="label -translate-y-0.5 bg-signal px-1.5 py-1 font-bold text-white">
                  {p.code}
                </span>
                <span
                  dir={p.dir}
                  className="text-[clamp(1.4rem,3.6vw,2rem)] font-semibold leading-tight tracking-[-0.01em]"
                >
                  {p.text}
                </span>
              </motion.div>
            </div>
          </motion.div>

          <motion.p
            initial={{ opacity: 0, y: 22 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, delay: 0.24 }}
            className="mt-8 max-w-[52ch] text-[clamp(1.05rem,2vw,1.3rem)] text-inkSoft"
          >
            Speech in, speech out, <span className="font-semibold text-ink">fully on your phone.</span>{" "}
            Nothing you say ever leaves the device. Works with a weak signal. Works with none.
          </motion.p>

          <motion.div
            initial={{ opacity: 0, y: 22 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, delay: 0.32 }}
            className="mt-9 flex flex-wrap items-center gap-3"
          >
            <Link
              href="https://github.com/JoeProAI/whatyousay/releases/download/app-v0.1.0/whatyousay-0.1.0.apk"
              className="label bg-signal px-6 py-4 font-bold text-paper transition-opacity hover:opacity-90"
            >
              Download APK / v0.1.0
            </Link>
            <Link
              href="#get"
              className="label border border-ink/40 px-5 py-4 text-ink transition-colors hover:border-signal hover:text-signal"
            >
              Android 8.0+ / how to install
            </Link>
          </motion.div>

          <motion.button
            type="button"
            onClick={() => setOffline((v) => !v)}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.8, delay: 0.4 }}
            aria-pressed={offline}
            className="mt-10 flex w-full max-w-[680px] items-center justify-between gap-4 border border-ink/50 bg-paper/80 px-4 py-4 text-left backdrop-blur-sm transition-colors hover:border-ink"
          >
            <span className="text-[0.95rem] text-inkSoft">
              {offline ? (
                <>
                  <strong className="font-semibold text-ink">Network off. Still translating.</strong> The
                  hero above did not even flinch. That is the whole product.
                </>
              ) : (
                <>
                  Don&apos;t trust it?{" "}
                  <strong className="font-semibold text-ink">Cut the network and watch.</strong> Nothing up
                  there needs the internet.
                </>
              )}
            </span>
            <span className="flex shrink-0 items-center gap-3">
              <span className="label text-inkSoft">{offline ? "No signal" : "Online"}</span>
              <span
                className={`relative h-7 w-[54px] border transition-colors ${
                  offline ? "border-signal bg-signal" : "border-ink bg-paper2"
                }`}
              >
                <span
                  className={`absolute top-0.5 h-[22px] w-[22px] bg-ink transition-all ${
                    offline ? "left-[28px]" : "left-0.5"
                  }`}
                />
              </span>
            </span>
          </motion.button>
        </div>
      </div>
    </section>
  );
}
