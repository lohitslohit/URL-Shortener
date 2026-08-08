# Take-home write-up — URL Shortener & Link Analytics

## 1. What I asked the AI to do, and what I decided myself

I treated the AI like a junior developer: I owned the design, gave clear specs, reviewed every change, and reworked anything that didn’t meet the bar. It accelerated boilerplate and iteration; it did not design the system.

The three decisions I considered most important were the short-code generation strategy, duplicate-URL behavior (idempotent reuse of an *active* mapping with `200` + `reused: true`), and custom alias handling (reserved words, `409` on conflict). Collision safety had to be explainable via DB uniqueness plus strategy-specific generation. Commit sequence followed that thinking: scaffold → API/model → hash → counter → refactor → stats/idempotency polish.

**I asked the AI (as that “junior”) to:** scaffold the Maven/Spring Boot project; draft controllers/DTOs/validation; implement the three short-code strategies behind a strategy switch; write unit/controller tests; and help tighten README run instructions. The AI’s initial project structure and test scaffolding were useful and saved time on setup — then I reviewed, corrected, and landed the result.

## 2. Where I overrode or threw away AI output — and why

- **Hash as the only generator:** Early AI output leaned on SHA-256 → Base62. That is fine for demos, but truncating a hash still needs a collision story. I kept hash as an option, added **random** (default: `SecureRandom` + retry + unique constraint) and **counter** (Base62 of DB id — unique by construction), and made the choice configurable. The commit history reflects that exploration rather than pretending one approach was “the” answer.
- **Naïve “always create a new row” for the same URL:** AI often treated each `POST /shorten` as insert-only. I rejected that for this product: same canonical URL should be idempotent while active. I added URL canonicalization and a unique `active_original_url` column (cleared on disable) so concurrency and “shorten again after disable” both have a clear model.
- **Over-abstracted or chatty code:** I pushed back on unnecessary layers, fixed API naming (`/shorten`, `/stats/{code}`), extracted `Base62` into a shared util, and kept the service hierarchy small (`UrlServiceImpl` + strategy subclasses).
- **Race handling:** Two concurrent requests shortening the same canonical URL could both attempt an insert. Retrying on the unique-constraint violation and then reading the existing mapping kept the API idempotent instead of returning a 500.

I intentionally kept the data model to a single primary mapping table. I considered separating analytics from URL mappings, but the assignment is primarily about correctness and explainability of the core shorten/redirect flow — so I chose simplicity over extensibility.

## 3. Biggest trade-offs

1. **Idempotent reuse vs. many codes per URL.** Reuse keeps the table smaller and matches typical shortener UX; the alternative (always mint a new code) is better for campaign tracking. I chose reuse for active mappings and documented it; disable clears the active unique key so the URL can be shortened again.
2. **Default random codes vs. counter/hash.** Counter is collision-free and short, but sequential IDs leak volume/order. Hash is deterministic but needs salt-on-collision after truncation. Random is opaque and simple; uniqueness is enforced in the DB with bounded retries. Default = random; other strategies remain for comparison in a follow-up.
3. **PostgreSQL + JPA vs. in-memory/Redis.** Postgres matches a production-shaped persistence model and unique constraints; Redis would be faster for redirects at scale but weaker for durable analytics without extra design. Given the exercise scope, Postgres was the better demonstration of data modeling.

## 4. What’s missing / another day

With another day I would focus on three things:

1. **Distributed ID generation** — Redis `INCR` for a centralized counter (avoids DB-sequence coupling / two-step insert), and/or Snowflake IDs for collision-resistant codes across multiple app instances
2. **Rate limiting / abuse checks** on `POST /shorten`
3. **Integration tests** with Testcontainers Postgres, including concurrency coverage for create races
