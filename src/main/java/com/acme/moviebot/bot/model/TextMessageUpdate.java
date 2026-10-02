package com.acme.moviebot.bot.model;

public record TextMessageUpdate(long updateId, long userId, long chatId, String text) implements BotUpdate {
}
