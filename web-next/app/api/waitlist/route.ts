import { NextResponse } from "next/server";

type Body = { email?: unknown };

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function isValidEmail(value: unknown): value is string {
  return typeof value === "string" && value.length <= 254 && EMAIL_RE.test(value);
}

/**
 * Persists a waitlist signup. Backend is chosen by environment, in order:
 *   1. Convex   - set CONVEX_WAITLIST_URL to an HTTP action that accepts { email }.
 *   2. Firebase - set FIREBASE_PROJECT_ID + FIREBASE_API_KEY (Firestore REST).
 *   3. Fallback - log server-side so the UX still works in preview.
 */
async function persist(email: string): Promise<{ ok: boolean; error?: string }> {
  const convexUrl = process.env.CONVEX_WAITLIST_URL;
  if (convexUrl) {
    const res = await fetch(convexUrl, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, source: "whatyousay.ai" }),
    });
    return res.ok ? { ok: true } : { ok: false, error: "Could not save right now." };
  }

  const projectId = process.env.FIREBASE_PROJECT_ID;
  const apiKey = process.env.FIREBASE_API_KEY;
  if (projectId && apiKey) {
    const collection = process.env.WAITLIST_COLLECTION ?? "waitlist";
    const url = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/${collection}?key=${apiKey}`;
    const res = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        fields: {
          email: { stringValue: email },
          source: { stringValue: "whatyousay.ai" },
          createdAt: { timestampValue: new Date().toISOString() },
        },
      }),
    });
    return res.ok ? { ok: true } : { ok: false, error: "Could not save right now." };
  }

  console.warn(
    `[waitlist] No backend configured (CONVEX_WAITLIST_URL or FIREBASE_PROJECT_ID/FIREBASE_API_KEY). Dropped signup: ${email}`,
  );
  return { ok: true };
}

export async function POST(request: Request) {
  let body: Body;
  try {
    body = (await request.json()) as Body;
  } catch {
    return NextResponse.json({ ok: false, error: "Invalid request." }, { status: 400 });
  }

  if (!isValidEmail(body.email)) {
    return NextResponse.json({ ok: false, error: "Enter a valid email." }, { status: 400 });
  }

  try {
    const result = await persist(body.email);
    return NextResponse.json(result, { status: result.ok ? 200 : 502 });
  } catch {
    return NextResponse.json({ ok: false, error: "Could not save right now." }, { status: 502 });
  }
}
