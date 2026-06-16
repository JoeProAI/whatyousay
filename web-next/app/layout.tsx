import type { Metadata } from "next";
import { Space_Grotesk, JetBrains_Mono } from "next/font/google";
import "./globals.css";

const grotesk = Space_Grotesk({
  subsets: ["latin"],
  weight: ["400", "500", "600", "700"],
  variable: "--font-grotesk",
  display: "swap",
});

const mono = JetBrains_Mono({
  subsets: ["latin"],
  weight: ["400", "500", "700"],
  variable: "--font-mono",
  display: "swap",
});

export const metadata: Metadata = {
  metadataBase: new URL("https://whatyousay.ai"),
  title: "WhatYouSay — translation that works in the deadzone",
  description:
    "Fully on-device voice and text translation for Android. Nothing you say ever leaves your phone. Works with a weak signal. Works with none.",
  openGraph: {
    title: "WhatYouSay — translation that works in the deadzone",
    description:
      "Fully on-device voice and text translation for Android. Nothing you say ever leaves your phone.",
    url: "https://whatyousay.ai",
    siteName: "WhatYouSay",
    images: [{ url: "/renders/hero.png", width: 1536, height: 1024 }],
    type: "website",
  },
  twitter: {
    card: "summary_large_image",
    title: "WhatYouSay — translation that works in the deadzone",
    description:
      "Fully on-device voice and text translation for Android. Nothing you say ever leaves your phone.",
    images: ["/renders/hero.png"],
  },
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" className={`${grotesk.variable} ${mono.variable}`}>
      <head>
        <noscript>
          <style>{".reveal{opacity:1!important;transform:none!important}"}</style>
        </noscript>
      </head>
      <body className="font-sans antialiased">{children}</body>
    </html>
  );
}
