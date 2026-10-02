CREATE TABLE processed_telegram_update (
    update_id BIGINT NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (update_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
