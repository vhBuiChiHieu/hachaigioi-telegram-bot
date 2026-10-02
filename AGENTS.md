# Repository Guide

## Project

- Java 21, Maven, Spring Boot 3.5, Spring Modulith, and MySQL.
- This is a single deployable Maven application organized into the `access`, `catalog`, `bot`, and `telegram` modules.

## Source layout

```text
src/main/java/com/acme/moviebot/
├── MovieBotApplication.java
├── access/
│   ├── AccessControl.java
│   └── internal/                 # Admin ID configuration and authorization implementation
├── catalog/
│   ├── CatalogManagement.java    # Public write API
│   ├── CatalogQuery.java         # Public read API
│   ├── CatalogCommands.java      # Write command records
│   ├── CatalogViews.java         # Read DTOs
│   └── internal/
│       ├── application/          # Transactional catalog services
│       ├── domain/               # Movie, Season, Episode, media entities and rules
│       └── persistence/          # Catalog-owned Spring Data repositories
├── bot/
│   ├── model/                    # Transport-neutral updates and bot actions
│   └── internal/
│       ├── admin/                # Authorization, persisted admin sessions and flows
│       ├── user/                 # Search and selection flows
│       └── ...                   # Routing, callback codec and update idempotency
└── telegram/
    └── internal/
        ├── client/               # Telegram Bot API HTTP client and DTOs
        ├── config/               # Telegram properties and HTTP client setup
        ├── executor/             # BotAction to Telegram API adapter
        ├── mapper/               # Telegram Update to BotUpdate mapping
        └── polling/              # Long polling and update processing

src/main/resources/
├── application*.yml
└── db/migration/                 # Versioned Flyway migrations
```

## Package responsibilities

- `access` decides whether a Telegram numeric user ID is an admin. Do not use usernames for authorization.
- `catalog` owns movie, season, episode, media, search, and publish rules. Keep its entities and repositories internal.
- `bot` owns transport-neutral command routing, user/admin conversations, persistent admin sessions, update idempotency, and `BotAction` creation.
- `telegram` owns Telegram HTTP calls, polling, mapping, retries, and action execution. Keep catalog and authorization policy out of this module.

## Build and run

- Compile with `./mvnw -q compile` or, on Windows, `./mvnw.cmd -q compile`.
- Run the application with `./mvnw spring-boot:run` or `./mvnw.cmd spring-boot:run` from the repository root.
- Run tests with `./mvnw test` or `./mvnw.cmd test` when the change or request calls for them.
- Start local MySQL with `docker compose up -d mysql`.
- Spring Boot imports the optional root `.env` file. Operating-system environment variables and command-line arguments take precedence. Never print or commit secrets; keep real credentials in `.env`, which is ignored by Git.

## Architecture

- Keep module dependencies directed from `telegram` to `bot`, and from `bot` to the public APIs of `access` and `catalog`.
- `access` and `catalog` must not depend on `bot` or `telegram`.
- Keep Telegram API DTOs and HTTP calls in `telegram`. Bot handlers use the transport-neutral models in `bot` and return `BotAction` values.
- Keep JPA entities and repositories inside the module that owns them. Other modules use public facades and DTOs, not persistence internals.
- Preserve Spring Modulith boundaries and avoid circular module dependencies.

## Persistence and configuration

- Flyway owns the database schema. Add a new versioned migration for schema changes; do not edit migrations that may already have been applied.
- Keep Hibernate schema management set to `validate`; do not use `ddl-auto: update`.
- Store secrets only in environment variables or local `.env`; update `.env.example` with empty or safe sample values when configuration changes.
- Do not log Telegram tokens, database passwords, or other secret values.
