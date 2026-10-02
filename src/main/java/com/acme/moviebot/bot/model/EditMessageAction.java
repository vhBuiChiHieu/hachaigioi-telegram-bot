package com.acme.moviebot.bot.model;

import java.util.List;

public record EditMessageAction(long chatId, long messageId, String text, List<List<InlineButton>> keyboard) implements BotAction {
}
