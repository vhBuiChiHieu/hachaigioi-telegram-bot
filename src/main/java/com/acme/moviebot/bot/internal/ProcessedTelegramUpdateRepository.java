package com.acme.moviebot.bot.internal;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedTelegramUpdateRepository extends JpaRepository<ProcessedTelegramUpdate, Long> {
}
