package com.acme.moviebot.bot.model;

public record SendVideoAction(long chatId, String fileId, String caption) implements BotAction {
}
