package com.acme.moviebot.bot.model;

public record SendPhotoAction(long chatId, String fileId, String caption) implements BotAction {
}
