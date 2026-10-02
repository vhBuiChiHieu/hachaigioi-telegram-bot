package com.acme.moviebot.bot.internal.admin;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminSessionRepository extends JpaRepository<AdminSession, Long> {

    Optional<AdminSession> findByTelegramUserIdAndChatId(long telegramUserId, long chatId);

    long deleteByTelegramUserIdAndChatId(long telegramUserId, long chatId);

    long deleteByExpiresAtBefore(LocalDateTime expiresAt);
}
