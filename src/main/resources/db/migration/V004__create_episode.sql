CREATE TABLE episode (
    id BIGINT NOT NULL AUTO_INCREMENT,
    season_id BIGINT NOT NULL,
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
    CONSTRAINT fk_episode_season FOREIGN KEY (season_id) REFERENCES season(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
