CREATE TABLE event_publication (
    id BINARY(16) NOT NULL,
    completion_date TIMESTAMP(6) NULL,
    event_type VARCHAR(512) NOT NULL,
    listener_id VARCHAR(512) NOT NULL,
    publication_date TIMESTAMP(6) NOT NULL,
    serialized_event VARCHAR(4000) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_event_publication_completion_date (completion_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
