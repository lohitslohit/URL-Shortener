# URL Shortener (Spring Boot + PostgreSQL)

A minimal URL shortener API built with Spring Boot, Spring Data JPA, and PostgreSQL.

## Features

- Create short URLs
- Reuse existing active short URL for duplicate input
- Redirect short code to original URL
- Disable short URLs
- Global API error handling
- Unit and controller tests
- Configurable short-code strategy:
	- Random strategy
	- Hash strategy (canonicalize URL + hash + base62)
	- Counter strategy (DB sequence ID + base62)

## Architecture

- Controller layer
- Service layer
- Repository layer (Spring Data JPA derived queries)
- DTOs
- Exception handling

## API

### 1) Create short URL

- Method: POST
- Path: /api/urls
- Request JSON:

```json
{
	"originalUrl": "https://example.com/some/path"
}
```

- Behavior:
	- 201 Created when a new mapping is generated
	- 200 OK when an active mapping already exists (reused=true)

### 2) Redirect by short code

- Method: GET
- Path: /{code}
- Behavior:
	- 302 Found with Location header when active code exists
	- 404 Not Found when code is missing or disabled

### 3) Disable short URL

- Method: DELETE
- Path: /api/urls/{code}
- Behavior:
	- 204 No Content when disable succeeds
	- 404 Not Found when code is missing

### 4) Health

- Method: GET
- Path: /health

### Error payload

Errors use this shape:

```json
{
	"message": "...",
	"path": "/...",
	"timestamp": "..."
}
```

## Configuration

`src/main/resources/application.properties` supports:

- DB_URL (default: jdbc:postgresql://localhost:5432/url_shorten)
- DB_USERNAME (default: postgres)
- DB_PASSWORD (default: sa)
- SHORT_CODE_STRATEGY (default: random)
- SHORT_CODE_HASH_LENGTH (default: 8, hash strategy only)

Application properties:

- app.short-code.strategy
	- random: random base62 code generation
	- hash: canonicalize URL, hash, base62 encode, and trim to configured length
	- counter: Base62-encodes the database row ID; guaranteed collision-free with no additional checks
- app.short-code.hash-length
	- Length of generated hash-based short code (hash strategy only)

## Run

1. Ensure PostgreSQL is running on localhost:5432.
2. Create database url_shorten.
3. Set environment variables if defaults do not match your local setup.
4. Run:

```bash
mvn spring-boot:run
```

Example (PowerShell):

```powershell
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="sa"
$env:DB_URL="jdbc:postgresql://localhost:5432/url_shorten"
$env:SHORT_CODE_STRATEGY="hash"
$env:SHORT_CODE_HASH_LENGTH="8"
mvn spring-boot:run
```

Counter strategy:

```powershell
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="sa"
$env:DB_URL="jdbc:postgresql://localhost:5432/url_shorten"
$env:SHORT_CODE_STRATEGY="counter"
mvn spring-boot:run
```

## Test

```bash
mvn test
```

## Notes

- If startup says port 8080 is already in use, stop the process using that port or change server.port.
- If startup shows authentication failure, verify DB_USERNAME/DB_PASSWORD.
- In hash strategy, canonical-equivalent URLs (for example default HTTPS port and trailing slash differences) resolve to the same lookup URL.
- In counter strategy, each new URL gets a short code derived from its unique database row ID (Base62-encoded). At 1 billion URLs the code is 6 characters (`"15ftgG"`); at 62^6 ≈ 56 billion it grows to 7.
