package com.acme.moviebot.bot.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "processed_telegram_update")
public class ProcessedTelegramUpdate {

    @Id
    @Column(name = "update_id")
    private long updateId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private LocalDateTime processedAt;

    protected ProcessedTelegramUpdate() {
    }

    public ProcessedTelegramUpdate(long updateId) {
        this.updateId = updateId;
    }

    @PrePersist
    protected void onCreate() { processedAt = LocalDateTime.now(ZoneOffset.UTC); }
}
