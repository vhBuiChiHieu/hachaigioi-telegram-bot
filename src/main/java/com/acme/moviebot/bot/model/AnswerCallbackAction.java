package com.acme.moviebot.bot.model;

public record AnswerCallbackAction(String callbackQueryId, String text, boolean showAlert) implements BotAction {
}
