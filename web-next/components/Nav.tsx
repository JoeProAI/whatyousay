import Link from "next/link";

export function Nav() {
  return (
    <header className="fixed inset-x-0 top-0 z-50 border-b border-ink/15 bg-paper/70 backdrop-blur-md">
      <div className="shell flex h-16 items-center justify-between">
        <Link href="#top" className="flex items-center gap-2.5 text-[1.06rem] font-bold tracking-[-0.02em]">
          <span className="flex h-4 items-end gap-[2px]" aria-hidden>
            <i className="block w-[3px] bg-signal" style={{ height: 5 }} />
            <i className="block w-[3px] bg-signal" style={{ height: 9 }} />
            <i className="block w-[3px] bg-signal" style={{ height: 13 }} />
            <i className="block w-[3px] bg-signal" style={{ height: 17 }} />
          </span>
          WhatYouSay
        </Link>
        <nav className="flex items-center gap-7" aria-label="Primary">
          <Link href="#why" className="label hidden text-inkSoft transition-colors hover:text-ink sm:block">
            Why
          </Link>
          <Link href="#how" className="label hidden text-inkSoft transition-colors hover:text-ink sm:block">
            How it works
          </Link>
          <Link href="#anywhere" className="label hidden text-inkSoft transition-colors hover:text-ink sm:block">
            Anywhere
          </Link>
          <Link
            href="#get"
            className="label border border-signal bg-signal px-3.5 py-2 font-bold text-ink transition-transform hover:-translate-y-0.5"
          >
            Get notified
          </Link>
        </nav>
      </div>
    </header>
  );
}
