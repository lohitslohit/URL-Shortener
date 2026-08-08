# URL Shortener (Spring Boot + PostgreSQL)

Step 2: API design and implementation baseline.

This repository includes a minimal and clean layered implementation:
- Controller layer
- Service layer
- Repository layer
- DTOs
- Global exception handling
- Basic unit and web tests

## API Design

### 1) Create short URL
- `POST /api/urls`
- Request body:
	- `originalUrl` (required, must start with `http://` or `https://`)
- Behavior:
	- Returns `201 Created` when a new short code is generated
	- Returns `200 OK` when same URL already exists (duplicate reuse)

### 2) Redirect by short code
- `GET /{code}`
- Behavior:
	- Returns `302 Found` with `Location` header when code exists and is active
	- Returns `404 Not Found` when code does not exist or is disabled

### 3) Disable short URL
- `DELETE /api/urls/{code}`
- Behavior:
	- Returns `204 No Content` when disable succeeds
	- Returns `404 Not Found` when code does not exist

### Error payload
- Errors use a shared response object with fields:
	- `message`
	- `path`
	- `timestamp`

## Run

1. Ensure PostgreSQL is running.
2. Create database: `url_shorten`
3. Set optional environment variables if needed:
	- `DB_URL`
	- `DB_USERNAME`
	- `DB_PASSWORD`
4. Run: `mvn spring-boot:run`

## Test

Run: `mvn test`
