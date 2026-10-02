CREATE TABLE movie (
    id BIGINT NOT NULL AUTO_INCREMENT,
    vietnamese_name VARCHAR(255) NOT NULL,
    chinese_name VARCHAR(255) NULL,
    search_name VARCHAR(512) NOT NULL,
    description TEXT NULL,
    thumbnail_file_id VARCHAR(1024) NULL,
    is_full BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_movie_search_name (search_name),
    KEY idx_movie_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
