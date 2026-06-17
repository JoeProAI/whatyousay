import { v } from "convex/values";
import { internalMutation } from "./_generated/server";

export const add = internalMutation({
  args: { email: v.string(), source: v.string() },
  handler: async (ctx, { email, source }) => {
    const existing = await ctx.db
      .query("waitlist")
      .withIndex("by_email", (q) => q.eq("email", email))
      .first();
    if (existing) {
      return { deduped: true };
    }
    await ctx.db.insert("waitlist", { email, source, createdAt: Date.now() });
    return { deduped: false };
  },
});
