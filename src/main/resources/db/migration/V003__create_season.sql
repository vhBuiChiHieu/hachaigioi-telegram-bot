CREATE TABLE season (
    id BIGINT NOT NULL AUTO_INCREMENT,
    movie_id BIGINT NOT NULL,
    season_number INT NOT NULL,
    original_episode_count INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_season_movie_number (movie_id, season_number),
    KEY idx_season_movie_id (movie_id),
    KEY idx_season_status (status),
    CONSTRAINT fk_season_movie FOREIGN KEY (movie_id) REFERENCES movie(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
