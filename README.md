# Telegram Movie Bot

Backend Telegram bot theo kiến trúc modular monolith, Java 25, Spring Boot và Maven. MySQL lưu catalog, admin session và idempotency key; video được gửi lại bằng Telegram `file_id`.

## Chạy local

1. Sao chép `.env.example` thành `.env` và điền `TELEGRAM_BOT_TOKEN`, `TELEGRAM_ADMIN_IDS`.
2. Khởi động MySQL: `docker compose up -d mysql`.
3. Chạy ứng dụng: `./mvnw spring-boot:run` (Windows: `./mvnw.cmd spring-boot:run`).
4. Kiểm tra `http://localhost:8001/actuator/health`.

Spring Boot import `.env` dưới dạng properties khi chạy từ thư mục gốc repo. Biến môi trường của hệ điều hành và command line sẽ ghi đè giá trị trong `.env`. Flyway tự chạy migrations khi Spring Boot khởi động. Ứng dụng dùng `ddl-auto: validate`; schema chỉ thay đổi qua migration.

Nếu chưa cấu hình bot token, ứng dụng vẫn khởi động nhưng Telegram polling tự tắt. Có thể override database bằng `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`; profile `prod` cần các biến này và `TELEGRAM_BOT_TOKEN`.

## Telegram commands

- USER: `/start`, `/help`, `/find <tên phim>`.
- ADMIN: `/admin`, `/cancel`; tìm nội dung DRAFT hoặc PUBLISHED bằng nút `Tìm phim` trong menu.
- Admin flow: tạo phim → tạo season → tạo episode → gửi video → publish từng cấp.

ADMIN được xác định duy nhất bằng Telegram numeric user ID trong `TELEGRAM_ADMIN_IDS` (comma-separated). Admin session hết hạn sau 30 phút và được persist trong MySQL.

## Maven

- `./mvnw spring-boot:run` — chạy local.
- `./mvnw package` — đóng gói `target/movie-bot.jar`.
- `./mvnw test` — chạy test suite khi được bổ sung.

## Module boundaries

```text
telegram → bot → access
                 catalog
```

`bot` gọi public APIs trong `access` và `catalog`; adapter Telegram không chứa business rules. Spring Modulith khai báo dependency cho các module.

## Process log

Các mốc và thay đổi triển khai được ghi trong [`process.md`](process.md). Yêu cầu kiến trúc và phạm vi V1 nằm trong [`docs/telegram-movie-bot-technical-spec.md`](docs/telegram-movie-bot-technical-spec.md).
