CREATE TABLE admin_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
