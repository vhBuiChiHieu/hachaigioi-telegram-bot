package com.acme.moviebot.bot.model;

public record CallbackUpdate(long updateId, long userId, long chatId, String callbackQueryId, String data,
                             Long messageId) implements BotUpdate {
    public CallbackUpdate(long updateId, long userId, long chatId, String callbackQueryId, String data) {
        this(updateId, userId, chatId, callbackQueryId, data, null);
    }
}
