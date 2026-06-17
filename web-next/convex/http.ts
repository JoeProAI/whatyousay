import { httpRouter } from "convex/server";
import { httpAction } from "./_generated/server";
import { internal } from "./_generated/api";

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function json(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

const waitlist = httpAction(async (ctx, request) => {
  let body: { email?: unknown; source?: unknown };
  try {
    body = await request.json();
  } catch {
    return json({ ok: false, error: "Invalid request." }, 400);
  }

  const email = typeof body.email === "string" ? body.email.trim() : "";
  if (email.length > 254 || !EMAIL_RE.test(email)) {
    return json({ ok: false, error: "Enter a valid email." }, 400);
  }

  const source = typeof body.source === "string" ? body.source : "whatyousay.ai";
  await ctx.runMutation(internal.waitlist.add, { email, source });
  return json({ ok: true }, 200);
});

const http = httpRouter();
http.route({ path: "/waitlist", method: "POST", handler: waitlist });

export default http;
