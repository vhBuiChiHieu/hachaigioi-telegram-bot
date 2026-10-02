package com.acme.moviebot.bot.model;

import java.util.List;

public record SendTextAction(long chatId, String text, List<List<InlineButton>> keyboard) implements BotAction {

    public SendTextAction {
        keyboard = keyboard == null ? List.of() : keyboard.stream().map(List::copyOf).toList();
    }

    public SendTextAction(long chatId, String text) {
        this(chatId, text, List.of());
    }
}
