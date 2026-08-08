# URL Shortener & Link Analytics (Spring Boot + PostgreSQL)

A small URL shortener that creates short codes, redirects with HTTP 301, supports custom aliases, and tracks click counts.

## Features

- `POST /shorten` — create a short code for a long URL
- `GET /{code}` — **301** redirect to the original URL
- Custom aliases (optional)
- Deliberate duplicate-URL handling (reuse active mapping)
- Click analytics (`GET /stats/{code}`)
- Soft-disable short URLs
- Configurable short-code strategy: random, hash, or counter
- URL validation and global API error handling
- Unit and controller tests

## Design decisions (required by the take-home)

### Duplicate URLs

If an **active** mapping already exists for the same canonical URL, `POST /shorten` returns that short code with `reused: true` and HTTP **200** (no second active mapping). Canonicalization lowercases scheme/host, strips default ports, normalizes trailing slash, and drops fragments.

After a short URL is disabled, the same long URL can be shortened again and may receive a **new** code.

If a custom `alias` is supplied for a URL that already has an active mapping, the existing code is returned and the new alias is ignored.

### Custom aliases

Optional `alias` on create:

- Base62 characters only (`0-9`, `a-z`, `A-Z`)
- Length 3–32
- Reserved: `api`, `health`, `urls`, `shorten`, `stats`
- Taken aliases return **409 Conflict**

### Short codes and collisions

- **random** (default): 7-char Base62 from `SecureRandom`, retry until unique (DB unique on `short_code`)
- **hash**: SHA-256 of canonical URL → Base62, trim; salt with `#n` on collision
- **counter**: Base62 of DB row id (unique by construction)

## API

### 1) Shorten — `POST /shorten`

```json
{
  "originalUrl": "https://example.com/some/path",
  "alias": "docs"
}
```

`alias` is optional.

- **201 Created** — new mapping (`reused=false`)
- **200 OK** — existing active mapping returned (`reused=true`)
- **400** — invalid URL / alias
- **409** — alias already taken

### 2) Redirect — `GET /{code}`

- **301 Moved Permanently** with `Location` header when active
- **404** when missing or disabled  
  Each successful redirect increments `clickCount`.

### 3) Stats — `GET /stats/{code}`

Returns short code, original URL, `clickCount`, `createdAt`, and `disabledAt` (if disabled).

### 4) Disable — `DELETE /api/urls/{code}`

- **204** on success
- **404** when missing / already disabled

### 5) Health — `GET /health`

## Run

1. PostgreSQL must be running on `localhost:5432` (default user `postgres` / password `sa`).
2. On startup the app **creates the `url_shorten` database if missing**, then Hibernate **`ddl-auto=update` creates/updates tables** (e.g. `url_mappings`) from entities.
3. Set env vars if needed (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`).
4. Run:

```bash
mvn spring-boot:run
```

PowerShell example:

```powershell
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="sa"
$env:DB_URL="jdbc:postgresql://localhost:5432/url_shorten"
$env:SHORT_CODE_STRATEGY="random"
mvn spring-boot:run
```

Strategies: `SHORT_CODE_STRATEGY=random|hash|counter`  
Hash length: `SHORT_CODE_HASH_LENGTH` (default `8`).

## Test

```bash
mvn test
```

## Write-up

See [WRITEUP.md](WRITEUP.md) for the required one-page exercise write-up.
