# URL Shortener (Spring Boot + PostgreSQL)

Step 1: Setup-only project skeleton.

This repository currently includes only a minimal and clean structure:
- Controller layer
- Service layer
- Repository layer
- DTOs
- Global exception handling
- Basic unit and web tests

Business API flows and full URL shortener data model will be added in the next steps.

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
