package com.acme.moviebot.bot.model;

public record CallbackUpdate(long updateId, long userId, long chatId, String callbackQueryId, String data) implements BotUpdate {
}
