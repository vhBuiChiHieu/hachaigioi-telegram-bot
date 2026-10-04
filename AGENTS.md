# Repository Guide

## Project

- Java 25, Maven, Spring Boot 3.5, Spring Modulith, and MySQL.
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
│   ├── BotCommandMenus.java      # User/admin slash-command menus
│   ├── model/                    # Transport-neutral updates and bot actions
│   └── internal/
│       ├── commands/             # Role-specific command menu definitions
│       ├── admin/                # Authorization, persisted admin sessions and flows
│       ├── user/                 # Search and selection flows
│       └── ...                   # Routing, callback codec and update idempotency
└── telegram/
    └── internal/
        ├── commands/             # Telegram command menu registration
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

- `access` decides whether a Telegram numeric user ID is an admin and exposes configured admin IDs through its public API. Do not use usernames for authorization.
- `catalog` owns movie, season, episode, media, search, and publish rules. Keep its entities and repositories internal.
- A movie stores Vietnamese and Chinese names, a Telegram thumbnail file ID, description, and full flag. A season stores the original Chinese episode count; each episode row is an ordered posted part, with order derived from its ID.
- Movie search ignores case, diacritics, and punctuation, then ranks token matches by Levenshtein similarity. Public `/find` results include published movies; admin search excludes archived movies.
- `bot` owns transport-neutral command routing, user/admin conversations, persistent admin sessions, update idempotency, `BotAction` creation, and user/admin command menu definitions.
- `/menu` welcomes users with inline Find and Help buttons; `/start` opens the same menu. Telegram command menus advertise `/menu`, `/help`, and `/find`. Find buttons use the same next-message keyword flow as `/find`; opening the menu clears pending user search input.
- Public movie navigation shows published seasons; selecting a season sends each published part's content in order without a separate part selection: Telegram videos as videos, external links as text with the movie/season/part title and original URL. Report unavailable content together.
- Admin catalog navigation lists movies 20 per page by descending ID. Movie buttons show the page-wide index and Vietnamese name; the message lists each movie's ID, Chinese name, publication status, and full/currently-updating state. Page changes edit the same message. Movie details show “Quản lý mùa phim”; selecting it replaces that message's keyboard with add-season and season buttons. Selecting a season opens its ordered movie parts; returning opens the season list directly. Keep the add action first in season/part management and the selected movie/season status action last.
- Each season-list row pairs its navigation button with “Đăng tải”/“Lưu trữ”. These actions publish or return the season to DRAFT and refresh the same message's keyboard with the latest statuses; season-detail archive actions remain separate. Telegram controls inline-button widths; the bot only defines rows and button order.
- Season details show movie, season number, a Vietnamese status label, original episode count, and posted-part count. Ordered parts appear as keyboard rows with admin content preview and “Đăng tải”/“Lưu trữ”; publication changes refresh that message's keyboard. Adding a part accepts either a Telegram video or one valid HTTP(S) URL (up to 2048 characters) in the same persisted session; legacy WAITING_EPISODE_VIDEO sessions also accept both. Each new part has one content source; attachment locks serialize concurrent writes and reject additional sources. Publishing requires attached content; unpublishing returns the part and its selected content to DRAFT. Admin previews include draft videos and links through catalog admin APIs.
- Movie details also offer “Quản lý chung”, replacing the message keyboard with name/description editing, cover-photo editing, and an explicit enable/disable FULL action. Edits use persisted admin sessions; metadata input is `"<Chinese name>" "<Vietnamese name>" "<description>"` (description may span lines and contain quotes), and renaming refreshes normalized search names.
- `telegram` owns Telegram HTTP calls, polling, mapping, retries, command menu registration, and action execution. Keep catalog and authorization policy out of this module.

## Build and run

- Compile with `./mvnw -q compile` or, on Windows, `./mvnw.cmd -q compile`.
- Run the application with `./mvnw spring-boot:run` or `./mvnw.cmd spring-boot:run` from the repository root.
- Run tests with `./mvnw test` or `./mvnw.cmd test` when the change or request calls for them.
- Put tests under `src/test/java` using the matching production package. Use JUnit Jupiter, AssertJ, and Mockito for isolated tests; use Spring context and Testcontainers only when testing framework or database integration.
- Start local MySQL with `docker compose up -d mysql`.
- Spring Boot imports the optional root `.env` file. Operating-system environment variables and command-line arguments take precedence. Never print or commit secrets; keep real credentials in `.env`, which is ignored by Git.

## Architecture

- Keep module dependencies directed from `telegram` to `bot`, and from `bot` to the public APIs of `access` and `catalog`.
- `access` and `catalog` must not depend on `bot` or `telegram`.
- Keep Telegram API DTOs and HTTP calls in `telegram`. Bot handlers use the transport-neutral models in `bot` and return `BotAction` values.
- Keep JPA entities and repositories inside the module that owns them. Other modules use public facades and DTOs, not persistence internals.
- Catalog entities inherit shared DRAFT/PUBLISHED/ARCHIVED status, optimistic version, and created/updated timestamps from `CatalogEntity`.
- Preserve Spring Modulith boundaries and avoid circular module dependencies.

## Persistence and configuration

- Flyway owns the database schema. Add a new versioned migration for schema changes; do not edit migrations that may already have been applied.
- Keep Hibernate schema management set to `validate`; do not use `ddl-auto: update`.
- Store secrets only in environment variables or local `.env`; update `.env.example` with empty or safe sample values when configuration changes.
- Do not log Telegram tokens, database passwords, or other secret values.
- `src/main/resources/logback-spring.xml` keeps console logging and writes INFO and higher to `./logs/movie-bot.yyyy-MM-dd_HH.log`, rolling hourly in the JVM's default time zone and appending on restart.
