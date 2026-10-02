package com.acme.moviebot.bot.model;

public sealed interface BotAction permits SendTextAction, SendVideoAction, EditMessageAction, AnswerCallbackAction {
}
