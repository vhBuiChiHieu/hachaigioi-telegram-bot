# Telegram Movie Bot Backend — Technical Specification

> Version: 0.1.0  
> Status: Draft / Baseline Architecture  
> Core stack: Java 21, Spring Boot 3.5.16, Spring Modulith 1.4.x, MySQL 8, Flyway, Docker Compose (MySQL only)

---

## 1. Mục tiêu tài liệu

Tài liệu này định nghĩa kiến trúc kỹ thuật cho một backend service vận hành Telegram Bot dùng để quản lý và phân phối nội dung phim theo cấu trúc:

```text
Movie
└── Season
    └── Episode
        └── MediaAsset
```

Hệ thống có hai nhóm người dùng chính:

- **ADMIN**: tương tác với bot để tạo phim, tạo mùa, tạo tập, upload video và quản lý trạng thái publish.
- **USER**: tìm kiếm phim, chọn phim, chọn mùa, chọn tập và nhận video từ Telegram thông qua `file_id` đã lưu trước đó.

Mục tiêu kiến trúc:

- Dễ triển khai ở giai đoạn đầu.
- Một application Spring Boot duy nhất.
- Một database MySQL duy nhất.
- Module boundaries rõ ràng.
- Không phụ thuộc chặt business logic vào Telegram SDK/API.
- Dễ mở rộng sang Web Admin, REST API, webhook, object storage hoặc microservice sau này nếu thực sự cần.
- Hạn chế over-engineering ở phiên bản đầu.

---

# 2. Phạm vi V1

## 2.1. In scope

V1 bao gồm:

1. Nhận update từ Telegram bằng long polling.
2. Phân quyền ADMIN dựa trên Telegram User ID.
3. ADMIN tạo Movie.
4. ADMIN tạo Season thuộc Movie.
5. ADMIN tạo Episode thuộc Season.
6. ADMIN gửi video vào chat với bot.
7. Bot lấy metadata Telegram của video và lưu vào MySQL.
8. USER tìm Movie theo tên.
9. Bot trả kết quả tìm kiếm dạng Inline Keyboard.
10. USER chọn Movie.
11. USER chọn Season.
12. USER chọn Episode.
13. Bot dùng Telegram `file_id` để gửi video cho user.
14. Quản lý conversation state của ADMIN trong database.
15. Database migration bằng Flyway.
16. Docker Compose dùng để chạy MySQL local/dev.
17. Integration test bằng Testcontainers.
18. Health endpoint bằng Spring Boot Actuator.

## 2.2. Out of scope ở V1

Chưa triển khai:

- Redis.
- Kafka.
- RabbitMQ.
- Elasticsearch/OpenSearch/Meilisearch.
- S3/MinIO/R2.
- Streaming server riêng.
- Microservices.
- Kubernetes.
- Web Admin UI.
- REST API public.
- Thanh toán/subscription.
- DRM.
- Multi-bot.
- Multi-tenant.
- Recommendation engine.

Các thành phần này chỉ nên được thêm khi có nhu cầu thực tế.

---

# 3. Technology Stack

## 3.1. Core

```text
Java                  21
Spring Boot           3.5.16
Spring Modulith       1.4.x
MySQL                 8.x
Flyway
Maven
Docker / Docker Compose
```

## 3.2. Spring dependencies

Khuyến nghị:

```text
spring-boot-starter
spring-boot-starter-validation
spring-boot-starter-data-jpa
spring-boot-starter-actuator
spring-modulith-starter-core
spring-modulith-starter-jpa
mysql-connector-j
flyway-core
flyway-mysql
```

Testing:

```text
spring-boot-starter-test
spring-modulith-starter-test
testcontainers
mysql-testcontainers
assertj
```

HTTP client để gọi Telegram Bot API:

```text
Spring RestClient
```

Khuyến nghị không để domain/application layer phụ thuộc trực tiếp vào Telegram Java SDK.

---

# 4. Architectural Style

Hệ thống sử dụng:

> Modular Monolith + Layered/Hexagonal boundaries bên trong từng module.

Một deployable duy nhất:

```text
movie-bot.jar
```

Một database:

```text
MySQL
```

Nhưng code được chia thành các business module độc lập về dependency.

Kiến trúc cấp cao:

```text
                          Telegram
                              │
                              │ updates
                              ▼
                    ┌───────────────────┐
                    │     telegram      │
                    │                   │
                    │ polling           │
                    │ API client        │
                    │ DTO mapper        │
                    └─────────┬─────────┘
                              │ BotUpdate
                              ▼
                    ┌───────────────────┐
                    │        bot        │
                    │                   │
                    │ command routing   │
                    │ callback routing  │
                    │ admin flow        │
                    │ user flow         │
                    └──────┬──────┬─────┘
                           │      │
                           │      └─────────────┐
                           ▼                    ▼
                  ┌────────────────┐    ┌────────────────┐
                  │     access     │    │    catalog     │
                  │                │    │                │
                  │ authorization  │    │ movie          │
                  │ principals     │    │ season         │
                  └────────────────┘    │ episode        │
                                        │ media          │
                                        │ search         │
                                        └───────┬────────┘
                                                │
                                                ▼
                                            ┌───────┐
                                            │ MySQL │
                                            └───────┘
```

---

# 5. Module Boundaries

V1 sử dụng bốn module chính:

```text
telegram
bot
access
catalog
```

## 5.1. Dependency graph

Dependency hợp lệ:

```text
telegram
    │
    ▼
   bot ─────────► access
    │
    ▼
 catalog
```

Không cho phép:

```text
catalog ─X─► bot
catalog ─X─► telegram
access  ─X─► bot
access  ─X─► telegram
```

## 5.2. Quy tắc module

- `catalog` không biết Telegram tồn tại.
- `access` không biết Telegram Bot API tồn tại.
- `bot` dùng abstraction/domain-neutral models thay vì Telegram SDK models.
- `telegram` là adapter ngoài cùng.
- Persistence implementation được giữ internal trong module sở hữu dữ liệu.
- Module khác chỉ gọi public API được expose.

---

# 6. Project Structure

Khuyến nghị sử dụng **single Maven module** ở V1.

```text
movie-bot/
├── pom.xml
├── docker-compose.yml
├── .env.example
├── README.md
├── Makefile                     # optional
├── docs/
│   └── architecture.md
│
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/acme/moviebot/
    │   │       ├── MovieBotApplication.java
    │   │       │
    │   │       ├── catalog/
    │   │       ├── access/
    │   │       ├── bot/
    │   │       └── telegram/
    │   │
    │   └── resources/
    │       ├── application.yml
    │       ├── application-local.yml
    │       ├── application-prod.yml
    │       └── db/migration/
    │
    └── test/
        └── java/
```

Chi tiết package:

```text
com.acme.moviebot
│
├── MovieBotApplication.java
│
├── catalog/
│   ├── package-info.java
│   ├── CatalogManagement.java
│   ├── CatalogQuery.java
│   │
│   ├── dto/
│   │   ├── MovieSummary.java
│   │   ├── MovieDetails.java
│   │   ├── SeasonSummary.java
│   │   ├── EpisodeSummary.java
│   │   └── EpisodeMediaView.java
│   │
│   └── internal/
│       ├── application/
│       │   ├── CatalogManagementService.java
│       │   ├── CatalogQueryService.java
│       │   └── MovieSearchService.java
│       │
│       ├── domain/
│       │   ├── Movie.java
│       │   ├── Season.java
│       │   ├── Episode.java
│       │   ├── MediaAsset.java
│       │   ├── MovieStatus.java
│       │   ├── SeasonStatus.java
│       │   ├── EpisodeStatus.java
│       │   ├── MediaProvider.java
│       │   └── MediaType.java
│       │
│       └── persistence/
│           ├── MovieJpaRepository.java
│           ├── SeasonJpaRepository.java
│           ├── EpisodeJpaRepository.java
│           ├── MediaAssetJpaRepository.java
│           └── MovieAliasJpaRepository.java
│
├── access/
│   ├── package-info.java
│   ├── AccessControl.java
│   ├── PrincipalView.java
│   │
│   └── internal/
│       ├── AccessControlService.java
│       ├── TelegramPrincipal.java
│       └── TelegramPrincipalRepository.java
│
├── bot/
│   ├── package-info.java
│   ├── BotUpdateHandler.java
│   │
│   ├── model/
│   │   ├── BotUpdate.java
│   │   ├── TextMessageUpdate.java
│   │   ├── CallbackUpdate.java
│   │   ├── MediaMessageUpdate.java
│   │   ├── IncomingMedia.java
│   │   ├── BotAction.java
│   │   ├── SendTextAction.java
│   │   ├── SendVideoAction.java
│   │   ├── EditMessageAction.java
│   │   └── AnswerCallbackAction.java
│   │
│   └── internal/
│       ├── BotUpdateRouter.java
│       ├── CallbackDataCodec.java
│       │
│       ├── admin/
│       │   ├── AdminCommandHandler.java
│       │   ├── AdminCallbackHandler.java
│       │   ├── AdminConversationService.java
│       │   ├── AdminSession.java
│       │   ├── AdminSessionState.java
│       │   └── AdminSessionRepository.java
│       │
│       └── user/
│           ├── UserCommandHandler.java
│           ├── SearchMovieHandler.java
│           ├── SelectMovieHandler.java
│           ├── SelectSeasonHandler.java
│           └── SelectEpisodeHandler.java
│
└── telegram/
    ├── package-info.java
    │
    └── internal/
        ├── polling/
        │   ├── TelegramPollingScheduler.java
        │   └── TelegramUpdateProcessor.java
        │
        ├── mapper/
        │   └── TelegramUpdateMapper.java
        │
        ├── executor/
        │   └── TelegramActionExecutor.java
        │
        ├── client/
        │   ├── TelegramBotClient.java
        │   ├── TelegramBotApiClient.java
        │   └── dto/
        │
        └── config/
            └── TelegramProperties.java
```

---

# 7. Domain Model

## 7.1. Movie

Đại diện một bộ phim/series.

Fields:

```text
id
slug
name
original_name
search_name
description
poster_file_id
status
created_at
updated_at
version
```

Suggested status:

```text
DRAFT
PUBLISHED
ARCHIVED
```

Rules:

- Movie mới mặc định `DRAFT`.
- User chỉ được xem Movie có status `PUBLISHED`.
- ADMIN có thể xem cả DRAFT.
- `slug` unique.

---

## 7.2. Season

Fields:

```text
id
movie_id
season_number
name
status
created_at
updated_at
version
```

Constraint:

```sql
UNIQUE(movie_id, season_number)
```

Rules:

- Season thuộc đúng một Movie.
- `season_number >= 0`.
- Có thể hỗ trợ Special bằng `season_number = 0` nếu cần.

---

## 7.3. Episode

Fields:

```text
id
season_id
episode_number
name
description
status
created_at
updated_at
version
```

Constraint:

```sql
UNIQUE(season_id, episode_number)
```

Rules:

- Episode thuộc đúng một Season.
- User chỉ thấy Episode `PUBLISHED`.

---

## 7.4. MediaAsset

Không lưu binary video vào MySQL.

V1 lưu metadata của file Telegram.

Fields:

```text
id
episode_id
provider
media_type
provider_file_id
provider_unique_file_id
source_chat_id
source_message_id
file_name
mime_type
file_size
duration_seconds
width
height
created_at
updated_at
version
```

Enum:

```text
MediaProvider
- TELEGRAM

MediaType
- VIDEO
```

Ý nghĩa:

```text
provider_file_id
    = Telegram file_id

provider_unique_file_id
    = Telegram file_unique_id
```

Ứng dụng sử dụng `provider_file_id` để gửi lại video.

`provider_unique_file_id` dùng cho nhận diện/deduplicate, không dùng để send video.

Thiết kế `Episode 1:N MediaAsset` thay vì 1:1 để dễ mở rộng:

```text
Episode
├── video 720p
├── video 1080p
├── Vietnamese subtitle
├── English subtitle
└── dubbed version
```

V1 có thể enforce application rule chỉ một `VIDEO/TELEGRAM` active cho mỗi Episode nếu muốn đơn giản.

---

# 8. Database Schema

Các bảng V1:

```text
movie
movie_alias
season
episode
media_asset
telegram_principal
admin_session
processed_telegram_update
```

## 8.1. movie

```sql
CREATE TABLE movie (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    slug VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    original_name VARCHAR(255) NULL,
    search_name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    poster_file_id VARCHAR(512) NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_movie_slug (slug),
    KEY idx_movie_search_name (search_name),
    KEY idx_movie_status (status)
);
```

---

## 8.2. movie_alias

```sql
CREATE TABLE movie_alias (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    movie_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(255) NOT NULL,
    search_name VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_movie_alias_movie_name (movie_id, search_name),
    KEY idx_movie_alias_search_name (search_name),
    CONSTRAINT fk_movie_alias_movie
        FOREIGN KEY (movie_id) REFERENCES movie(id)
);
```

---

## 8.3. season

```sql
CREATE TABLE season (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    movie_id BIGINT UNSIGNED NOT NULL,
    season_number INT NOT NULL,
    name VARCHAR(255) NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_season_movie_number (movie_id, season_number),
    KEY idx_season_movie_id (movie_id),
    KEY idx_season_status (status),
    CONSTRAINT fk_season_movie
        FOREIGN KEY (movie_id) REFERENCES movie(id)
);
```

---

## 8.4. episode

```sql
CREATE TABLE episode (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    season_id BIGINT UNSIGNED NOT NULL,
    episode_number INT NOT NULL,
    name VARCHAR(255) NULL,
    description TEXT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_episode_season_number (season_id, episode_number),
    KEY idx_episode_season_id (season_id),
    KEY idx_episode_status (status),
    CONSTRAINT fk_episode_season
        FOREIGN KEY (season_id) REFERENCES season(id)
);
```

---

## 8.5. media_asset

```sql
CREATE TABLE media_asset (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    episode_id BIGINT UNSIGNED NOT NULL,

    provider VARCHAR(32) NOT NULL,
    media_type VARCHAR(32) NOT NULL,

    provider_file_id VARCHAR(1024) NOT NULL,
    provider_unique_file_id VARCHAR(512) NULL,

    source_chat_id BIGINT NULL,
    source_message_id BIGINT NULL,

    file_name VARCHAR(512) NULL,
    mime_type VARCHAR(128) NULL,
    file_size BIGINT NULL,
    duration_seconds INT NULL,
    width INT NULL,
    height INT NULL,

    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),
    KEY idx_media_asset_episode_id (episode_id),
    KEY idx_media_asset_unique_file_id (provider_unique_file_id),
    CONSTRAINT fk_media_asset_episode
        FOREIGN KEY (episode_id) REFERENCES episode(id)
);
```

---

## 8.6. telegram_principal

```sql
CREATE TABLE telegram_principal (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    telegram_user_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_telegram_principal_user (telegram_user_id)
);
```

Roles:

```text
ADMIN
USER
```

V1 có thể chưa persist USER nếu không cần. ADMIN có thể seed từ config hoặc migration.

---

## 8.7. admin_session

```sql
CREATE TABLE admin_session (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    telegram_user_id BIGINT NOT NULL,
    chat_id BIGINT NOT NULL,
    flow VARCHAR(64) NOT NULL,
    state VARCHAR(64) NOT NULL,
    context_json JSON NULL,
    expires_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_session_user_chat (telegram_user_id, chat_id),
    KEY idx_admin_session_expires_at (expires_at)
);
```

---

## 8.8. processed_telegram_update

```sql
CREATE TABLE processed_telegram_update (
    update_id BIGINT NOT NULL,
    processed_at DATETIME(6) NOT NULL,

    PRIMARY KEY (update_id)
);
```

Mục đích:

- chống xử lý duplicate update;
- hỗ trợ idempotency cho admin commands;
- tránh tạo Episode/MediaAsset hai lần.

Có thể cleanup record cũ theo retention policy, ví dụ 30 ngày.

---

# 9. Search Design

V1 dùng MySQL, chưa cần search engine riêng.

## 9.1. Search normalization

Khi lưu Movie:

```text
name = "Bố Già"
search_name = "bo gia"
```

Normalization pipeline:

```text
trim
lowercase
unicode normalize
remove Vietnamese diacritics
đ -> d
collapse whitespace
```

Ví dụ:

```text
"  BỐ   GIÀ "
        ↓
"bo gia"
```

## 9.2. Alias

Ví dụ Movie:

```text
Breaking Bad
```

Aliases:

```text
Tập Làm Người Xấu
BB
BreakingBad
```

Tất cả alias đều có `search_name` normalized.

## 9.3. Search query

V1 có thể bắt đầu bằng:

```sql
SELECT DISTINCT m.*
FROM movie m
LEFT JOIN movie_alias a ON a.movie_id = m.id
WHERE m.status = 'PUBLISHED'
  AND (
       m.search_name LIKE CONCAT('%', :keyword, '%')
       OR a.search_name LIKE CONCAT('%', :keyword, '%')
  )
ORDER BY m.name
LIMIT :limit;
```

Khi dataset lớn hơn mới tối ưu search strategy.

---

# 10. Public Module APIs

## 10.1. CatalogManagement

`bot` chỉ gọi public facade này để mutate catalog.

Ví dụ:

```java
public interface CatalogManagement {

    Long createMovie(CreateMovieCommand command);

    Long createSeason(CreateSeasonCommand command);

    Long createEpisode(CreateEpisodeCommand command);

    Long attachMedia(AttachMediaCommand command);

    void publishMovie(long movieId);

    void publishSeason(long seasonId);

    void publishEpisode(long episodeId);

    void archiveMovie(long movieId);
}
```

Commands nên là immutable records.

Ví dụ:

```java
public record CreateEpisodeCommand(
        long seasonId,
        int episodeNumber,
        String name,
        String description
) {}
```

---

## 10.2. CatalogQuery

```java
public interface CatalogQuery {

    List<MovieSummary> searchMovies(String keyword, int limit);

    Optional<MovieDetails> findMovie(long movieId);

    List<SeasonSummary> findPublishedSeasons(long movieId);

    List<EpisodeSummary> findPublishedEpisodes(long seasonId);

    Optional<EpisodeMediaView> findEpisodeMedia(long episodeId);
}
```

Không expose JPA Entity ra ngoài module.

---

## 10.3. AccessControl

```java
public interface AccessControl {

    boolean isAdmin(long telegramUserId);

    boolean isAllowed(long telegramUserId);
}
```

---

# 11. Telegram Adapter Design

Telegram module chịu trách nhiệm:

```text
Long Polling
Telegram HTTP request
Telegram DTO
Telegram API error handling
Telegram <-> Bot model mapping
Execute BotAction
```

Không chứa:

```text
Movie business rules
Season business rules
Episode business rules
Admin authorization policy
Conversation business state
```

---

# 12. Internal Bot Model

Không truyền trực tiếp Telegram `Update` vào business handlers.

Sử dụng internal model.

```java
public sealed interface BotUpdate
        permits TextMessageUpdate,
                CallbackUpdate,
                MediaMessageUpdate {
}
```

## 12.1. Text message

```java
public record TextMessageUpdate(
        long updateId,
        long userId,
        long chatId,
        String text
) implements BotUpdate {}
```

## 12.2. Callback

```java
public record CallbackUpdate(
        long updateId,
        long userId,
        long chatId,
        String callbackQueryId,
        String data
) implements BotUpdate {}
```

## 12.3. Media

```java
public record MediaMessageUpdate(
        long updateId,
        long userId,
        long chatId,
        long messageId,
        IncomingMedia media
) implements BotUpdate {}
```

```java
public record IncomingMedia(
        String fileId,
        String fileUniqueId,
        String fileName,
        String mimeType,
        Long fileSize,
        Integer durationSeconds,
        Integer width,
        Integer height
) {}
```

---

# 13. Bot Actions

Handlers không gọi Telegram API trực tiếp.

Chúng trả về actions.

```java
public sealed interface BotAction
        permits SendTextAction,
                SendVideoAction,
                EditMessageAction,
                AnswerCallbackAction {
}
```

Ví dụ:

```java
public record SendVideoAction(
        long chatId,
        String fileId,
        String caption
) implements BotAction {}
```

Telegram adapter map:

```text
SendVideoAction
      ↓
Telegram sendVideo
```

Lợi ích:

- unit test dễ;
- bot module độc lập Telegram SDK;
- sau này đổi Telegram library ít ảnh hưởng;
- có thể thêm Discord/Web adapter mà không sửa catalog.

---

# 14. Callback Data Convention

Telegram callback data phải compact.

Convention V1:

```text
m:<movieId>
s:<seasonId>
e:<episodeId>
mp:<page>
sp:<movieId>:<page>
ep:<seasonId>:<page>
```

Ví dụ:

```text
m:101
s:205
e:999
sp:101:2
```

Implement bằng class riêng:

```text
CallbackDataCodec
```

Không parse string callback rải rác trong handlers.

---

# 15. USER Flow

## 15.1. `/start`

User:

```text
/start
```

Bot:

```text
Xin chào.

Bạn có thể tìm phim bằng:
/find <tên phim>
```

---

## 15.2. Search Movie

User:

```text
/find breaking bad
```

Flow:

```text
TextMessageUpdate
       │
       ▼
BotUpdateRouter
       │
       ▼
UserCommandHandler
       │
       ▼
CatalogQuery.searchMovies(...)
       │
       ▼
Inline keyboard
```

Response:

```text
Tìm thấy phim:

[Breaking Bad]
[Better Call Saul]
[El Camino]
```

Callbacks:

```text
m:101
m:102
m:103
```

---

## 15.3. Select Movie

Callback:

```text
m:101
```

Flow:

```text
CallbackUpdate
     │
     ├── AnswerCallbackAction
     │
     └── CatalogQuery.findPublishedSeasons(101)
```

Bot:

```text
Breaking Bad

Chọn mùa:

[Season 1]
[Season 2]
[Season 3]
[Season 4]
[Season 5]
```

---

## 15.4. Select Season

Callback:

```text
s:205
```

Bot query:

```text
CatalogQuery.findPublishedEpisodes(205)
```

Response:

```text
Breaking Bad - Season 1

[Tập 1 - Pilot]
[Tập 2 - Cat's in the Bag...]
[Tập 3 - ...]
```

---

## 15.5. Select Episode

Callback:

```text
e:999
```

Flow:

```text
CatalogQuery.findEpisodeMedia(999)
       │
       ▼
MediaAsset.provider_file_id
       │
       ▼
SendVideoAction
       │
       ▼
Telegram sendVideo(chatId, fileId)
```

Nếu chưa có video:

```text
Video của tập này chưa khả dụng.
```

---

# 16. ADMIN Flow

ADMIN authorization luôn kiểm tra Telegram numeric user ID.

Không dùng Telegram username làm authority key.

## 16.1. Entry point

Admin:

```text
/admin
```

Bot:

```text
Quản lý nội dung

[➕ Thêm phim]
[🔎 Tìm phim]
[❌ Hủy thao tác]
```

---

# 17. Admin Conversation State Machine

Conversation state được persist vào MySQL.

Không giữ state quan trọng bằng:

```java
Map<Long, AdminState>
```

vì application restart sẽ mất state.

Suggested states:

```text
IDLE

WAITING_MOVIE_NAME
WAITING_MOVIE_ORIGINAL_NAME
WAITING_MOVIE_DESCRIPTION

WAITING_SEASON_NUMBER
WAITING_SEASON_NAME

WAITING_EPISODE_NUMBER
WAITING_EPISODE_NAME
WAITING_EPISODE_DESCRIPTION
WAITING_EPISODE_VIDEO

CONFIRMING_PUBLISH
```

Flow context lưu JSON:

```json
{
  "movieId": 101,
  "seasonId": 205,
  "episodeId": 999
}
```

---

# 18. ADMIN Create Movie Flow

```text
/admin
   │
   ▼
[Thêm phim]
   │
   ▼
WAITING_MOVIE_NAME
```

Bot:

```text
Nhập tên phim:
```

Admin:

```text
Breaking Bad
```

Bot gọi:

```text
CatalogManagement.createMovie(...)
```

Movie mặc định:

```text
DRAFT
```

Response:

```text
Đã tạo phim: Breaking Bad

[➕ Thêm Season]
[✅ Publish]
[❌ Hủy]
```

---

# 19. ADMIN Create Season Flow

Admin chọn:

```text
[➕ Thêm Season]
```

State:

```text
WAITING_SEASON_NUMBER
```

Bot:

```text
Nhập số season:
```

Admin:

```text
1
```

Application:

```text
CatalogManagement.createSeason(movieId, 1, ...)
```

Response:

```text
Đã tạo Season 1

[➕ Thêm Episode]
```

---

# 20. ADMIN Create Episode Flow

```text
[➕ Thêm Episode]
       │
       ▼
WAITING_EPISODE_NUMBER
```

Admin:

```text
1
```

Bot:

```text
Nhập tên tập:
```

Admin:

```text
Pilot
```

Create:

```text
Episode #999
```

State chuyển:

```text
WAITING_EPISODE_VIDEO
```

Session context:

```json
{
  "movieId": 101,
  "seasonId": 205,
  "episodeId": 999
}
```

Bot:

```text
Hãy gửi video cho:
Season 1 - Episode 1 - Pilot
```

---

# 21. ADMIN Attach Video Flow

Admin gửi video vào chat.

Telegram update chứa metadata ví dụ:

```text
message_id
chat.id
video.file_id
video.file_unique_id
video.file_name
video.mime_type
video.file_size
video.duration
video.width
video.height
```

Telegram adapter map thành:

```text
MediaMessageUpdate
```

Bot module kiểm tra:

```text
AccessControl.isAdmin(userId)
        │
        ▼
AdminSession.state == WAITING_EPISODE_VIDEO
```

Sau đó:

```text
CatalogManagement.attachMedia(...)
```

Data lưu:

```text
episode_id = 999
provider = TELEGRAM
media_type = VIDEO
provider_file_id = <file_id>
provider_unique_file_id = <file_unique_id>
source_chat_id = <admin_chat_id>
source_message_id = <message_id>
```

Response:

```text
Video đã được gắn vào Episode 1 ✅

[✅ Publish Episode]
[➕ Thêm Episode tiếp theo]
```

---

# 22. Session Expiration

Admin session nên có TTL.

Ví dụ:

```text
30 minutes
```

Nếu session hết hạn:

```text
Thao tác quản trị đã hết hạn.
Vui lòng bắt đầu lại bằng /admin.
```

Có thể cleanup bằng scheduled job mỗi vài giờ.

Không cần Redis ở V1.

---

# 23. Idempotency

Mọi Telegram update có `update_id`.

Trước khi xử lý:

```text
Receive update
      │
      ▼
processed_telegram_update exists?
      │
 ┌────┴────┐
 │         │
yes        no
 │         │
ignore     process
            │
            ▼
        insert update_id
```

Đối với transaction quan trọng:

```text
@Transactional
process update
+ persist domain mutation
+ mark processed update
```

Nếu transaction rollback, update chưa được mark processed.

---

# 24. Transaction Boundaries

Application services là nơi khai báo transaction.

Ví dụ:

```java
@Transactional
public Long createEpisode(CreateEpisodeCommand command) {
    ...
}
```

Không mở transaction ở Telegram HTTP layer.

Suggested transaction use cases:

```text
createMovie
createSeason
createEpisode
attachMedia
publishMovie
publishEpisode
processAdminUpdate
```

Read-only:

```java
@Transactional(readOnly = true)
```

cho query services.

---

# 25. Concurrency

Entity mutate nên có optimistic locking:

```java
@Version
private long version;
```

Áp dụng cho:

```text
Movie
Season
Episode
MediaAsset
AdminSession
```

Mục đích:

- tránh lost update;
- an toàn hơn nếu sau này chạy >1 instance;
- bảo vệ admin conversation state.

---

# 26. Telegram Polling

V1 sử dụng long polling.

Pseudo flow:

```text
TelegramPollingScheduler
        │
        ▼
getUpdates(offset, timeout)
        │
        ▼
List<TelegramUpdate>
        │
        ▼
TelegramUpdateProcessor
        │
        ▼
TelegramUpdateMapper
        │
        ▼
BotUpdateHandler
        │
        ▼
List<BotAction>
        │
        ▼
TelegramActionExecutor
```

Offset chỉ advance sau khi update được xử lý phù hợp.

Polling phải có:

```text
HTTP timeout
retry/backoff
structured logging
shutdown handling
```

---

# 27. Telegram API Client

Interface internal:

```java
interface TelegramBotClient {

    List<TelegramUpdateDto> getUpdates(long offset, int timeoutSeconds);

    void sendMessage(...);

    void sendVideo(...);

    void editMessageText(...);

    void answerCallbackQuery(...);
}
```

Implementation:

```text
TelegramBotApiClient
```

sử dụng Spring `RestClient`.

Bot token lấy từ environment variable.

---

# 28. Error Handling

Phân loại lỗi:

## 28.1. Validation error

Ví dụ:

```text
season_number < 0
empty movie name
invalid callback data
```

Bot trả user-friendly message.

## 28.2. Domain conflict

Ví dụ:

```text
Season 1 already exists
Episode 10 already exists
```

Bot:

```text
Season 1 đã tồn tại.
```

## 28.3. Telegram API transient error

Ví dụ:

```text
HTTP 429
HTTP 5xx
network timeout
```

Có retry/backoff có giới hạn.

Không retry vô hạn.

## 28.4. Telegram API permanent error

Ví dụ:

```text
invalid file_id
chat not found
bot blocked
```

Log structured error và không retry liên tục.

## 28.5. Unexpected error

- log error với correlation/update id;
- không gửi stacktrace cho user;
- ADMIN có thể nhận generic error message.

---

# 29. Logging

Log các fields quan trọng:

```text
updateId
telegramUserId
chatId
command
callbackType
movieId
seasonId
episodeId
```

Không log:

```text
Telegram bot token
DB password
secret values
```

Recommended format:

```text
INFO  update.processed updateId=123 userId=456 type=CALLBACK durationMs=20
```

Production có thể dùng JSON logging sau này.

---

# 30. Security

## 30.1. Secrets

Không commit:

```text
Telegram bot token
DB password
```

Dùng environment variables.

## 30.2. Admin identification

Authority key:

```text
telegram_user_id
```

Không dùng:

```text
username
first_name
last_name
```

## 30.3. Database user

Không dùng MySQL root account cho application.

Tạo riêng:

```text
movie_bot
```

với quyền trên database ứng dụng.

---

# 31. Configuration

## 31.1. application.yml

```yaml
spring:
  application:
    name: telegram-movie-bot

  profiles:
    default: local

  jpa:
    open-in-view: false
    properties:
      hibernate:
        jdbc:
          time_zone: UTC

  flyway:
    enabled: true

management:
  endpoints:
    web:
      exposure:
        include: health,info

app:
  telegram:
    polling:
      enabled: true
      timeout-seconds: 30
```

---

## 31.2. application-local.yml

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/movie_bot?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
    username: movie_bot
    password: movie_bot

  jpa:
    hibernate:
      ddl-auto: validate

app:
  telegram:
    bot-token: ${TELEGRAM_BOT_TOKEN}
    admin-ids: ${TELEGRAM_ADMIN_IDS:}
```

---

## 31.3. application-prod.yml

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

  jpa:
    hibernate:
      ddl-auto: validate

app:
  telegram:
    bot-token: ${TELEGRAM_BOT_TOKEN}
    admin-ids: ${TELEGRAM_ADMIN_IDS:}
```

---

# 32. Telegram Configuration Properties

Ví dụ:

```java
@ConfigurationProperties(prefix = "app.telegram")
public record TelegramProperties(
        String botToken,
        Set<Long> adminIds,
        Polling polling
) {
    public record Polling(
            boolean enabled,
            int timeoutSeconds
    ) {}
}
```

Không inject secret bằng `@Value` rải rác.

---

# 33. Flyway Migration Layout

```text
src/main/resources/db/migration/
├── V001__create_movie.sql
├── V002__create_movie_alias.sql
├── V003__create_season.sql
├── V004__create_episode.sql
├── V005__create_media_asset.sql
├── V006__create_telegram_principal.sql
├── V007__create_admin_session.sql
└── V008__create_processed_telegram_update.sql
```

Production:

```text
ddl-auto = validate
```

Không dùng:

```text
ddl-auto = update
```

---

# 34. Docker Strategy

Ở giai đoạn hiện tại Docker **chỉ dùng cho MySQL**.

Spring Boot chạy trực tiếp:

```text
IDE
hoặc
./mvnw spring-boot:run
hoặc
java -jar target/movie-bot.jar
```

Topology local:

```text
┌───────────────────────────────┐
│ Host machine                  │
│                               │
│  Spring Boot                  │
│  localhost:8080               │
│       │                       │
│       │ localhost:3306        │
│       ▼                       │
│  ┌─────────────────────────┐  │
│  │ Docker                  │  │
│  │                         │  │
│  │ MySQL 8                 │  │
│  └─────────────────────────┘  │
└───────────────────────────────┘
```

---

# 35. docker-compose.yml

Recommended baseline:

```yaml
services:
  mysql:
    image: mysql:8.4
    container_name: telegram-movie-bot-mysql
    restart: unless-stopped

    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-root}
      MYSQL_DATABASE: ${MYSQL_DATABASE:-movie_bot}
      MYSQL_USER: ${MYSQL_USER:-movie_bot}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD:-movie_bot}
      TZ: UTC

    ports:
      - "3306:3306"

    volumes:
      - mysql_data:/var/lib/mysql

    healthcheck:
      test:
        [
          "CMD-SHELL",
          "mysqladmin ping -h localhost -u root -p$$MYSQL_ROOT_PASSWORD --silent"
        ]
      interval: 5s
      timeout: 5s
      retries: 10
      start_period: 20s

    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci

volumes:
  mysql_data:
```

Lưu ý:

- Dùng named volume để data không mất khi restart container.
- `docker compose down` không xóa volume.
- `docker compose down -v` sẽ xóa data local.
- Charset dùng `utf8mb4`.
- Production credentials không dùng default values như ví dụ local.

---

# 36. .env.example

```dotenv
MYSQL_ROOT_PASSWORD=root
MYSQL_DATABASE=movie_bot
MYSQL_USER=movie_bot
MYSQL_PASSWORD=movie_bot

TELEGRAM_BOT_TOKEN=
TELEGRAM_ADMIN_IDS=123456789,987654321
```

File thực tế:

```text
.env
```

phải nằm trong `.gitignore`.

---

# 37. Docker Commands

Start MySQL:

```bash
docker compose up -d mysql
```

Check status:

```bash
docker compose ps
```

Logs:

```bash
docker compose logs -f mysql
```

Stop:

```bash
docker compose stop mysql
```

Remove container nhưng giữ volume:

```bash
docker compose down
```

Reset toàn bộ local DB:

```bash
docker compose down -v
```

Sau đó:

```bash
docker compose up -d mysql
```

---

# 38. Local Development Flow

## 38.1. Prerequisites

```text
JDK 21
Docker
Docker Compose
Maven Wrapper
Telegram bot token
```

## 38.2. Start DB

```bash
docker compose up -d mysql
```

## 38.3. Set token

Linux/macOS:

```bash
export TELEGRAM_BOT_TOKEN="..."
export TELEGRAM_ADMIN_IDS="123456789"
```

Windows PowerShell:

```powershell
$env:TELEGRAM_BOT_TOKEN="..."
$env:TELEGRAM_ADMIN_IDS="123456789"
```

## 38.4. Start application

```bash
./mvnw spring-boot:run
```

Flyway tự chạy migration khi application startup.

---

# 39. Maven Baseline

Ví dụ `pom.xml` dependency section ở mức định hướng:

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.modulith</groupId>
        <artifactId>spring-modulith-starter-core</artifactId>
    </dependency>

    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <scope>runtime</scope>
    </dependency>

    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-core</artifactId>
    </dependency>

    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-mysql</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>

    <dependency>
        <groupId>org.springframework.modulith</groupId>
        <artifactId>spring-modulith-starter-test</artifactId>
        <scope>test</scope>
    </dependency>

    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>mysql</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

Nên quản lý Spring Modulith qua BOM tương thích với Spring Boot 3.5.x.

---

# 40. Spring Modulith Verification

Tạo architecture test:

```java
class ModularityTests {

    @Test
    void verifiesModuleStructure() {
        ApplicationModules.of(MovieBotApplication.class).verify();
    }
}
```

CI phải fail nếu:

```text
module cycle
illegal access internal package
invalid dependency
```

Có thể generate module documentation ở CI/dev.

---

# 41. Persistence Rules

## 41.1. Không expose JPA repositories

Các repository đặt trong:

```text
catalog.internal.persistence
```

Module `bot` không inject repository.

Không làm:

```java
class SearchMovieHandler {
    private final MovieJpaRepository repository;
}
```

Phải làm:

```java
class SearchMovieHandler {
    private final CatalogQuery catalogQuery;
}
```

## 41.2. Không expose JPA entities

Không trả `Movie` entity từ public API module.

Trả:

```text
MovieSummary
MovieDetails
SeasonSummary
EpisodeSummary
```

---

# 42. Entity Loading Strategy

Không dùng aggregate graph lớn:

```text
Movie -> Seasons -> Episodes -> Media
```

cho mọi query.

Query theo màn hình:

```text
search movie
    ↓
find seasons(movieId)
    ↓
find episodes(seasonId)
    ↓
find media(episodeId)
```

Phù hợp tự nhiên với UX Telegram bot.

Tránh vấn đề:

```text
N+1
huge entity graph
LazyInitializationException
excessive memory use
```

---

# 43. Pagination

Inline keyboard cần pagination sớm.

Suggested defaults:

```text
Movie search: 10 / page
Episode list: 10 / page
```

Callback:

```text
mp:2
sp:101:2
ep:205:3
```

Không gửi keyboard hàng trăm buttons trong một message.

---

# 44. Sending Multiple Episodes

V1 ưu tiên user chọn từng Episode.

Không mặc định gửi toàn bộ Season vì có thể tạo burst nhiều video.

Nếu thêm command:

```text
[Gửi cả mùa]
```

thì nên giới hạn và tuần tự hóa.

Future design:

```text
delivery_job
```

Fields:

```text
id
user_id
season_id
status
current_episode
created_at
updated_at
```

Chưa cần implement ở V1.

---

# 45. Health Check

Actuator:

```text
GET /actuator/health
```

Expected:

```json
{
  "status": "UP"
}
```

Health nên phản ánh ít nhất database connectivity.

Telegram connectivity có thể thêm custom health indicator sau.

---

# 46. Testing Strategy

## 46.1. Unit tests

Test:

```text
CallbackDataCodec
Search normalization
Admin state transitions
Catalog domain validation
Bot command parsing
```

Không cần Spring context cho các test này.

## 46.2. Module integration tests

Test public APIs:

```text
CatalogManagement
CatalogQuery
AccessControl
```

## 46.3. Repository integration tests

Dùng MySQL Testcontainers thay vì H2 để tránh khác biệt dialect.

## 46.4. Telegram adapter tests

Mock Telegram HTTP server hoặc mock `TelegramBotClient`.

Test mapping:

```text
Telegram Update -> BotUpdate
BotAction -> Telegram request
```

## 46.5. End-to-end use case tests

Các scenario quan trọng:

```text
ADMIN create movie -> season -> episode -> upload video
USER search -> select movie -> select season -> select episode -> sendVideo
Duplicate Telegram update is ignored
Unauthorized user cannot execute admin operation
Restart does not lose admin conversation state
```

---

# 47. Recommended Test Naming

Ví dụ:

```text
shouldCreateMovieWhenAdminProvidesValidName
shouldRejectDuplicateSeasonNumber
shouldAttachTelegramVideoToEpisode
shouldReturnPublishedMoviesOnly
shouldNormalizeVietnameseMovieNameForSearch
shouldIgnoreAlreadyProcessedTelegramUpdate
shouldRejectAdminCommandFromRegularUser
```

---

# 48. Observability V1

Minimum:

```text
Actuator health
structured application logs
update processing duration
Telegram API failure logging
DB connection metrics
```

Later:

```text
Micrometer
Prometheus
Grafana
OpenTelemetry
```

Không bắt buộc ở V1.

---

# 49. Failure Scenarios

## 49.1. MySQL unavailable

Application startup fail hoặc health DOWN.

Polling không nên tiếp tục mutate state nếu DB unavailable.

## 49.2. Telegram unavailable

Retry polling với bounded backoff.

## 49.3. Invalid stored file_id

Bot trả:

```text
Không thể gửi video này. Vui lòng báo quản trị viên.
```

Log:

```text
mediaAssetId
episodeId
Telegram error code
```

## 49.4. Bot restart while admin uploading

Do session nằm trong MySQL:

```text
restart
   ↓
load session
   ↓
continue WAITING_EPISODE_VIDEO
```

---

# 50. Recommended Command Set V1

## USER

```text
/start
/find <keyword>
/help
```

## ADMIN

```text
/admin
/cancel
```

Phần còn lại ưu tiên button + conversation flow thay vì quá nhiều slash commands.

Có thể thêm shortcut sau:

```text
/movie_add
/season_add
/episode_add
```

nhưng không bắt buộc V1.

---

# 51. Publish Model

Khuyến nghị nội dung mới luôn bắt đầu ở `DRAFT`.

Ví dụ:

```text
Movie DRAFT
    ↓
Season DRAFT
    ↓
Episode DRAFT
    ↓
Attach video
    ↓
Publish Episode
    ↓
Publish Season
    ↓
Publish Movie
```

USER query chỉ trả nội dung đủ điều kiện publish.

Ví dụ Episode cần:

```text
status == PUBLISHED
AND has VIDEO MediaAsset
```

---

# 52. Audit Fields

Mọi domain table nên có:

```text
created_at
updated_at
```

Nếu cần audit nâng cao sau này thêm:

```text
created_by
updated_by
```

với Telegram user ID.

Không bắt buộc trong first migration nếu muốn giữ V1 nhỏ.

---

# 53. Naming Convention

Java:

```text
Movie
Season
Episode
MediaAsset
```

Database:

```text
snake_case
```

IDs:

```text
BIGINT
```

Dates:

```text
UTC
DATETIME(6)
```

Enum persistence:

```text
VARCHAR
```

Không persist ordinal enum.

---

# 54. Repository Branching Recommendation

Simple workflow:

```text
main
feature/*
fix/*
```

CI trên PR:

```text
compile
unit tests
modularity verification
integration tests
```

---

# 55. Definition of Done cho một feature

Feature chỉ hoàn thành khi:

- business logic có test;
- Flyway migration có nếu schema thay đổi;
- module boundary không bị vi phạm;
- không expose persistence object ra module khác;
- Telegram errors được handle;
- user/admin response có message hợp lý;
- logs có đủ context;
- application start thành công với MySQL Docker local.

---

# 56. Suggested Implementation Milestones

## Milestone 1 — Bootstrap

```text
Spring Boot app
Java 21
Spring Modulith
MySQL Docker
Flyway
Actuator
```

Acceptance:

```text
docker compose up -d mysql
./mvnw spring-boot:run
/actuator/health = UP
```

## Milestone 2 — Catalog Core

Implement:

```text
Movie
Season
Episode
MediaAsset
CatalogManagement
CatalogQuery
```

## Milestone 3 — Telegram Skeleton

Implement:

```text
long polling
/start
/help
TelegramUpdateMapper
TelegramActionExecutor
```

## Milestone 4 — User Search

Implement:

```text
/find
Movie search
Movie selection
Season selection
Episode selection
```

## Milestone 5 — Access Control

Implement:

```text
ADMIN user IDs
/admin
unauthorized handling
```

## Milestone 6 — Admin State Machine

Implement:

```text
admin_session
create movie
create season
create episode
/cancel
```

## Milestone 7 — Video Attach

Implement:

```text
WAITING_EPISODE_VIDEO
video metadata extraction
MediaAsset persistence
```

## Milestone 8 — Delivery

Implement:

```text
Episode select
load MediaAsset
sendVideo(file_id)
```

## Milestone 9 — Hardening

Implement:

```text
processed update idempotency
pagination
retry/backoff
integration tests
logging
```

---

# 57. Potential Future Evolution

Kiến trúc hiện tại cho phép mở rộng mà không cần rewrite core.

## 57.1. Web Admin

Thêm:

```text
admin-web adapter
       │
       ▼
CatalogManagement
CatalogQuery
```

Telegram admin flow vẫn tồn tại.

## 57.2. Object Storage

Mở rộng:

```text
MediaProvider
- TELEGRAM
- S3
- MINIO
- R2
```

`catalog` vẫn quản lý metadata, delivery adapter quyết định cách gửi.

## 57.3. Search Engine

Thêm module/adapter:

```text
search
```

với:

```text
Meilisearch
OpenSearch
Elasticsearch
```

chỉ khi MySQL search thực sự không đủ.

## 57.4. Webhook

Thay:

```text
TelegramPollingScheduler
```

bằng:

```text
TelegramWebhookController
```

Downstream vẫn là:

```text
TelegramUpdateMapper
    ↓
BotUpdateHandler
```

Do đó bot/catalog logic gần như không đổi.

## 57.5. Multi-instance

Khi scale horizontal:

- dùng webhook hoặc cơ chế single poller leader;
- admin session đã ở DB;
- optimistic locking đã có;
- processed update idempotency đã có;
- có thể thêm distributed lock khi thật sự cần.

---

# 58. Architectural Decisions

## ADR-001 — Modular Monolith

**Decision:** sử dụng một deployable Spring Boot nhưng chia application modules rõ ràng.

**Reason:** domain chưa đủ lớn để justify microservices.

## ADR-002 — MySQL as primary persistence

**Decision:** dùng MySQL cho catalog, session và idempotency.

**Reason:** đơn giản, transactional, dễ vận hành.

## ADR-003 — Telegram files are referenced, not stored

**Decision:** lưu Telegram `file_id` và metadata thay vì binary video.

**Reason:** tránh lưu video nặng trong MySQL/application storage.

## ADR-004 — Long polling for V1

**Decision:** Telegram updates qua long polling.

**Reason:** dễ chạy local/VPS, không cần public webhook endpoint.

## ADR-005 — MySQL only in Docker during local development

**Decision:** Docker Compose ban đầu chỉ chạy MySQL.

**Reason:** developer có thể chạy Spring Boot trực tiếp từ IDE, debug dễ hơn.

## ADR-006 — Database-backed admin conversation state

**Decision:** persist admin session vào MySQL.

**Reason:** restart không mất flow, sẵn sàng cho scale sau này.

## ADR-007 — Flyway owns schema

**Decision:** schema changes quản lý bằng Flyway.

**Reason:** reproducible migrations và production-safe schema evolution.

---

# 59. Non-Functional Requirements

## Maintainability

- module boundaries được verify tự động;
- không circular dependency;
- không expose JPA internals;
- handlers nhỏ và single-purpose.

## Reliability

- idempotent Telegram update handling;
- optimistic locking;
- persistent conversation state;
- bounded retry.

## Performance

Initial target:

```text
Thousands to tens of thousands of movies
Thousands of concurrent users at burst level depending on Telegram limits and host sizing
```

Không tối ưu premature bằng distributed architecture.

## Security

- admin bằng numeric Telegram user ID;
- secrets qua environment;
- application DB account riêng;
- không log credentials.

---

# 60. V1 Acceptance Criteria

Hệ thống V1 được coi là usable khi đáp ứng end-to-end scenario:

### ADMIN

```text
/admin
→ create Movie
→ create Season
→ create Episode
→ send video
→ metadata lưu MySQL
→ publish
```

### USER

```text
/find breaking bad
→ select Movie
→ select Season
→ select Episode
→ bot sendVideo bằng stored Telegram file_id
```

### Infrastructure

```text
docker compose up -d mysql
→ MySQL healthy

./mvnw spring-boot:run
→ Flyway migration success
→ Spring Boot starts
→ Actuator health UP
→ Telegram polling starts
```

### Restart

```text
Admin đang ở WAITING_EPISODE_VIDEO
→ restart Spring Boot
→ gửi video
→ bot vẫn attach đúng Episode
```

### Idempotency

```text
cùng update_id được đưa vào hai lần
→ mutation chỉ xảy ra một lần
```

---

# 61. Recommended Initial Repository Files

```text
movie-bot/
├── .editorconfig
├── .gitignore
├── .env.example
├── docker-compose.yml
├── mvnw
├── mvnw.cmd
├── pom.xml
├── README.md
│
├── docs/
│   └── technical-spec.md
│
└── src/
    ├── main/
    │   ├── java/...
    │   └── resources/
    │       ├── application.yml
    │       ├── application-local.yml
    │       ├── application-prod.yml
    │       └── db/migration/...
    │
    └── test/
        └── java/...
```

Recommended `.gitignore` additions:

```gitignore
.env
.idea/
.vscode/
target/
*.log
```

---

# 62. Final Architecture Summary

Kiến trúc V1:

```text
                        ┌───────────────────┐
                        │ Telegram Bot API  │
                        └─────────┬─────────┘
                                  │
                            Long Polling
                                  │
                                  ▼
                       ┌────────────────────┐
                       │ telegram module    │
                       └──────────┬─────────┘
                                  │ BotUpdate
                                  ▼
                       ┌────────────────────┐
                       │ bot module         │
                       │                    │
                       │ user flow          │
                       │ admin state        │
                       │ callbacks          │
                       └───────┬──────┬─────┘
                               │      │
                     ┌─────────┘      └───────────┐
                     ▼                            ▼
            ┌─────────────────┐           ┌──────────────────┐
            │ catalog module  │           │ access module    │
            │                 │           │                  │
            │ Movie           │           │ ADMIN auth       │
            │ Season          │           │ Telegram IDs     │
            │ Episode         │           └──────────────────┘
            │ MediaAsset      │
            │ Search          │
            └────────┬────────┘
                     │
                     ▼
               ┌────────────┐
               │   MySQL    │
               │  Docker    │
               └────────────┘
```

Nguyên tắc chính:

```text
1 application
1 database
4 application modules
Telegram chỉ là adapter
MySQL giữ state quan trọng
Telegram giữ binary video
DB chỉ giữ file_id + metadata
Flyway quản lý schema
Docker hiện tại chỉ chạy MySQL
```

Đây là baseline đủ đơn giản để bắt đầu nhanh nhưng vẫn có ranh giới kiến trúc tốt để hệ thống mở rộng mà không cần rewrite toàn bộ.
