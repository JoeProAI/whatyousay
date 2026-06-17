# WhatYouSay — marketing site

Full-page, image-led marketing site for WhatYouSay (whatyousay.ai). Next.js (App Router) +
TypeScript + Tailwind. The visual backbone is a set of AI renders in `public/renders/` in a
constructivist palette (signal orange `#FF4A17` / ink `#0E0E0C` / paper `#F3F0E8`).

## Develop

```
npm install
npm run dev      # http://localhost:3000
npm run build    # production build (type-checks + lints)
npm start        # serve the production build
```

Node >= 18.18 (Next 14). Built and verified on Node 22.

## Waitlist

The hero and final CTA share a "Get notified when it drops" waitlist (it replaces the old
"Download the APK" button, since the shipping APK today is the stub build). The form posts to
`app/api/waitlist/route.ts`, which selects a backend from environment variables:

| Backend  | Env vars                                            | Behavior                                   |
| -------- | --------------------------------------------------- | ------------------------------------------ |
| Convex   | `CONVEX_WAITLIST_URL`                               | POSTs `{ email, source }` to an HTTP action |
| Firebase | `FIREBASE_PROJECT_ID`, `FIREBASE_API_KEY`           | Writes a doc to Firestore via REST          |
| Fallback | (none set)                                          | Logs the signup server-side, returns ok     |

Optional: `WAITLIST_COLLECTION` (Firestore collection name, default `waitlist`).

### Firebase (Firestore) setup

1. In the Firebase console, create/choose a project. Copy its **Project ID** and a **Web API key**
   (Project settings -> General).
2. Set `FIREBASE_PROJECT_ID` and `FIREBASE_API_KEY` in Vercel project env vars.
3. Add a Firestore security rule allowing create-only on the waitlist collection:

   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /waitlist/{doc} {
         allow create: if request.resource.data.email is string;
         allow read, update, delete: if false;
       }
     }
   }
   ```

### Convex setup

The Convex backend lives in `convex/`:

- `schema.ts` — a `waitlist` table (`email`, `source`, `createdAt`) indexed by email.
- `waitlist.ts` — `add`, an internal mutation that inserts a signup (deduped by email).
- `http.ts` — a `POST /waitlist` HTTP action that validates `{ email, source }` and runs the mutation.

Deploy it and wire the env var:

1. Generate a deploy key in the Convex dashboard (Deployment Settings -> Deploy Keys).
2. Deploy the functions from this directory:

   ```
   CONVEX_DEPLOY_KEY=<key> npx convex deploy
   ```

3. Set `CONVEX_WAITLIST_URL` in Vercel to the deployment's HTTP Actions URL plus `/waitlist`,
   e.g. `https://<deployment>.convex.site/waitlist`.

The `convex/` directory is excluded from the Next build (`tsconfig.json`); Convex type-checks it
with its own `convex/tsconfig.json` at deploy time.

## Deploy on Vercel

This site lives in the `web-next/` subdirectory of the WhatYouSay repo. When importing the repo
into Vercel, set **Root Directory = `web-next`**. Framework preset auto-detects as Next.js. Add the
waitlist env vars above, then point the `whatyousay.ai` domain at the Vercel project.
