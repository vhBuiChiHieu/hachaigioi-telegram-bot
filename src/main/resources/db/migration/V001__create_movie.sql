CREATE TABLE movie (
    id BIGINT NOT NULL AUTO_INCREMENT,
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
