package com.acme.moviebot.bot.model;

public sealed interface BotAction permits SendTextAction, SendPhotoAction, SendVideoAction, EditMessageAction,
        EditMessageKeyboardAction, AnswerCallbackAction {
}
