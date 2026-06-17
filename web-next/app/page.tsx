import Image from "next/image";
import Link from "next/link";
import { Nav } from "@/components/Nav";
import { Hero } from "@/components/Hero";
import { Reveal } from "@/components/Reveal";
import { Waitlist } from "@/components/Waitlist";

const PILLARS = [
  {
    n: "A",
    title: "Nothing leaves.",
    body: "Private by construction, not by privacy policy. There is no network code in the translation path. The only thing this app downloads is the models, once, on wifi, when you ask.",
  },
  {
    n: "B",
    title: "A conversation, not a search box.",
    body: "Put the phone between two people. It hears who is speaking and which language, then answers in the other. Nobody taps a language picker mid-sentence.",
  },
  {
    n: "C",
    title: "Built for the dead zone.",
    body: "Subways, borders, basements, back-country, seat 30A at altitude. Where every other translator goes blank, this one keeps talking.",
  },
];

const STAGES = [
  { k: "In", title: "You speak", model: "mic / VAD" },
  { k: "Transcribe", title: "Speech to text", model: "whisper.cpp" },
  { k: "Translate", title: "Text to text", model: "Hunyuan Hy-MT2 / 440MB" },
  { k: "Speak", title: "Text to speech", model: "Piper" },
];

export default function Page() {
  return (
    <main id="top">
      <Nav />
      <Hero />

      {/* 01 — WHY */}
      <section id="why" className="border-t border-line bg-paper py-[clamp(64px,10vw,128px)]">
        <div className="shell">
          <Reveal className="mb-12 flex items-baseline gap-4">
            <span className="label font-bold text-signal">01</span>
            <h2 className="max-w-[20ch] text-[clamp(1.8rem,4.4vw,3rem)] font-semibold leading-[1.02] tracking-[-0.025em]">
              Not another offline pack bolted onto a cloud app.
            </h2>
          </Reveal>

          <div className="grid items-stretch gap-0 lg:grid-cols-[1.05fr_0.95fr] lg:border lg:border-line">
            <div className="relative min-h-[340px] overflow-hidden border border-line lg:border-0 lg:border-r">
              <Image
                src="/renders/nothing-leaves.webp"
                alt="A phone holding its sound waves inside its own frame, network signal crossed out"
                fill
                sizes="(max-width: 1024px) 100vw, 50vw"
                className="object-cover"
              />
            </div>
            <div className="grid grid-rows-3">
              {PILLARS.map((p, i) => (
                <Reveal
                  key={p.n}
                  delay={i * 0.08}
                  className="border-x border-b border-line p-7 lg:border-x-0 lg:border-t-0 lg:border-b lg:last:border-b-0"
                >
                  <div className="label font-bold text-signal">{p.n}</div>
                  <h3 className="mb-2.5 mt-3 text-[1.4rem] font-semibold tracking-[-0.02em]">{p.title}</h3>
                  <p className="text-inkSoft">{p.body}</p>
                </Reveal>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* 02 — HOW (full-bleed pipeline render) */}
      <section id="how" className="relative border-t border-line bg-ink text-paper">
        <div className="relative h-[clamp(280px,38vw,520px)] w-full overflow-hidden">
          <Image
            src="/renders/pipeline.webp"
            alt="Constructivist diagram: ear and microphone, transforming gears, mouth and speaker"
            fill
            sizes="100vw"
            className="object-cover"
          />
          <div className="absolute inset-0 bg-gradient-to-t from-ink via-ink/20 to-transparent" />
        </div>
        <div className="shell -mt-[6vw] pb-[clamp(64px,10vw,128px)]">
          <Reveal className="relative mb-12 flex items-baseline gap-4">
            <span className="label font-bold text-signal">02</span>
            <div>
              <h2 className="max-w-[18ch] text-[clamp(1.8rem,4.4vw,3rem)] font-semibold leading-[1.02] tracking-[-0.025em]">
                The whole pipeline fits on the phone.
              </h2>
              <p className="mt-3 max-w-[60ch] text-[clamp(1.05rem,1.8vw,1.25rem)] text-paper/60">
                Three stages, all local. Open models running on your hardware, swappable like cartridges.
              </p>
            </div>
          </Reveal>

          <Reveal className="grid border border-paper/20 sm:grid-cols-2 lg:grid-cols-4">
            {STAGES.map((s, i) => (
              <div
                key={s.k}
                className="relative border-b border-paper/20 p-6 sm:border-r last:border-r-0 lg:border-b-0"
              >
                <div className="label text-paper/50">{s.k}</div>
                <h4 className="mb-2 mt-3 text-[1.18rem] font-semibold tracking-[-0.01em]">{s.title}</h4>
                <div className="label text-signal">{s.model}</div>
                {i < STAGES.length - 1 && (
                  <span className="absolute -right-px top-1/2 hidden h-2.5 w-2.5 -translate-y-1/2 rotate-45 border-r border-t border-signal bg-ink lg:block" />
                )}
              </div>
            ))}
          </Reveal>

          <Reveal className="mt-8 flex items-center gap-3">
            <span className="label font-bold text-signal">[ Missing ]</span>
            <span className="text-[clamp(1.05rem,1.8vw,1.25rem)] text-paper/70">
              Notice what is not in this diagram: a server. That is the entire point.
            </span>
          </Reveal>

          <Reveal className="mt-5 max-w-[70ch] text-paper/70">
            Need a language Hy-MT2 doesn&apos;t cover? Drop in the{" "}
            <code className="font-mono border border-paper/30 px-1.5 py-0.5 text-paper">NLLB-200</code> pack for
            200+. Want flagship quality?{" "}
            <code className="font-mono border border-paper/30 px-1.5 py-0.5 text-paper">TranslateGemma</code> with
            NPU acceleration. The model is disposable. The privacy is not.
          </Reveal>
        </div>
      </section>

      {/* 03 — ANYWHERE + conversation */}
      <section id="anywhere" className="border-t border-line bg-paper py-[clamp(64px,10vw,128px)]">
        <div className="shell">
          <div className="grid items-center gap-10 lg:grid-cols-[0.9fr_1.1fr]">
            <Reveal className="relative aspect-square overflow-hidden border border-line">
              <Image
                src="/renders/anywhere.webp"
                alt="A faceted globe with off-grid regions still pinned, network signal crossed out"
                fill
                sizes="(max-width: 1024px) 100vw, 45vw"
                className="object-cover"
              />
            </Reveal>
            <div>
              <Reveal className="mb-8 flex items-baseline gap-4">
                <span className="label font-bold text-signal">03</span>
                <h2 className="max-w-[16ch] text-[clamp(1.8rem,4.4vw,3rem)] font-semibold leading-[1.02] tracking-[-0.025em]">
                  One phone. Two people. No shared language.
                </h2>
              </Reveal>

              <Reveal className="grid border border-line bg-paper2 sm:grid-cols-[1fr_1px_1fr]">
                <div className="p-7">
                  <div className="label mb-3.5 flex justify-between text-inkSoft">
                    <span>Speaker A</span>
                    <span>EN</span>
                  </div>
                  <div className="mb-2.5 text-[1.3rem] font-medium tracking-[-0.01em]">
                    &ldquo;Can you help me find the pharmacy?&rdquo;
                  </div>
                  <div className="text-inkSoft">heard / translated / spoken aloud in Portuguese</div>
                </div>
                <div className="hidden bg-line sm:block" />
                <div className="border-t border-line p-7 sm:border-t-0 sm:text-right">
                  <div className="label mb-3.5 flex justify-between text-inkSoft">
                    <span>PT</span>
                    <span>Speaker B</span>
                  </div>
                  <div className="mb-2.5 text-[1.3rem] font-medium tracking-[-0.01em]">
                    &ldquo;Claro, fica logo ali à direita.&rdquo;
                  </div>
                  <div className="text-inkSoft">heard / translated / spoken aloud in English</div>
                </div>
              </Reveal>
              <Reveal className="mt-5 text-inkSoft">
                It flips direction on its own. You just talk.
              </Reveal>
            </div>
          </div>
        </div>
      </section>

      {/* GET — waitlist CTA */}
      <section id="get" className="relative overflow-hidden border-t border-line bg-ink text-paper">
        <div className="grid-paper absolute inset-0 opacity-[0.06]" aria-hidden />
        <div className="shell relative py-[clamp(72px,11vw,150px)] text-center">
          <Reveal>
            <h2 className="mx-auto max-w-[16ch] text-[clamp(2rem,6.4vw,3.8rem)] font-bold leading-[1] tracking-[-0.03em]">
              Get it. Then lose signal <span className="text-signal">on purpose.</span>
            </h2>
          </Reveal>
          <Reveal delay={0.08} className="mt-7 flex flex-wrap justify-center gap-2.5">
            {["Android / 8.0+", "~600MB with models", "v0.1 / open source", "No account / no trackers"].map(
              (c) => (
                <span key={c} className="label border border-paper/25 px-3 py-2 text-paper/70">
                  {c}
                </span>
              ),
            )}
          </Reveal>
          <Reveal delay={0.16} className="mt-9 flex flex-col items-center gap-4">
            <p className="max-w-[52ch] text-paper/70">
              The build is in active development. Drop your email and we will send the APK the moment it ships.
            </p>
            <div className="flex w-full justify-center [&_input]:bg-ink [&_input]:text-paper [&_input]:placeholder:text-paper/40">
              <Waitlist compact />
            </div>
            <Link
              href="https://github.com/JoeProAI"
              className="label mt-1 border border-paper/25 px-4 py-3 text-paper transition-colors hover:border-signal hover:text-signal"
            >
              View on GitHub
            </Link>
          </Reveal>
        </div>
      </section>

      {/* FOOTER */}
      <footer className="bg-ink pb-10 pt-[clamp(56px,8vw,96px)] text-paper">
        <div className="shell">
          <div className="text-[clamp(3rem,13vw,9rem)] font-bold leading-[0.9] tracking-[-0.04em]">
            what you <span className="text-signal">say?</span>
          </div>
          <div className="mt-12 flex flex-wrap justify-between gap-8 border-t border-paper/15 pt-7">
            <div>
              <p className="label mb-3.5 text-paper/40">Product</p>
              {[
                ["Why it's different", "#why"],
                ["How it works", "#how"],
                ["Works anywhere", "#anywhere"],
                ["Get notified", "#get"],
              ].map(([label, href]) => (
                <Link key={label} href={href} className="block py-1 text-paper/70 transition-colors hover:text-signal">
                  {label}
                </Link>
              ))}
            </div>
            <div>
              <p className="label mb-3.5 text-paper/40">Build</p>
              <Link href="https://github.com/JoeProAI" className="block py-1 text-paper/70 transition-colors hover:text-signal">
                GitHub
              </Link>
              <span className="block py-1 text-paper/70">Open models</span>
              <span className="block py-1 text-paper/70">On-device privacy</span>
            </div>
          </div>
          <div className="label mt-10 flex flex-wrap justify-between gap-3 text-paper/40">
            <span>WhatYouSay / whatyousay.ai / on-device translation</span>
            <span className="text-signal">No trackers on this page either.</span>
          </div>
        </div>
      </footer>
    </main>
  );
}
