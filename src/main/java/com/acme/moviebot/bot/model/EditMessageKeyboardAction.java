package com.acme.moviebot.bot.model;

import java.util.List;

public record EditMessageKeyboardAction(long chatId, long messageId,
                                        List<List<InlineButton>> keyboard) implements BotAction {
}
