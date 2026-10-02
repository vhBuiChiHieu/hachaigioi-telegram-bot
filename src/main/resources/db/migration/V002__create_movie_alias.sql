CREATE TABLE movie_alias (
    id BIGINT NOT NULL AUTO_INCREMENT,
    movie_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    search_name VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_movie_alias_movie_name (movie_id, search_name),
    KEY idx_movie_alias_search_name (search_name),
    CONSTRAINT fk_movie_alias_movie FOREIGN KEY (movie_id) REFERENCES movie(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
