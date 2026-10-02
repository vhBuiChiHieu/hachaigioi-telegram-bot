package com.acme.moviebot.bot.model;

public record MediaMessageUpdate(
        long updateId,
        long userId,
        long chatId,
        long messageId,
        IncomingMedia media) implements BotUpdate {
}
