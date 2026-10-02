package com.acme.moviebot.bot.model;

public sealed interface BotUpdate permits TextMessageUpdate, CallbackUpdate, MediaMessageUpdate {

    long updateId();

    long userId();

    long chatId();
}
