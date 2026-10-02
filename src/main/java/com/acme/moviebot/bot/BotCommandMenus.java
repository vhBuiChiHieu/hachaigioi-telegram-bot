package com.acme.moviebot.bot;

import java.util.List;

public interface BotCommandMenus {

    List<Command> userCommands();

    List<AdminMenu> adminMenus();

    record Command(String command, String description) {
    }

    record AdminMenu(long telegramUserId, List<Command> commands) {
        public AdminMenu {
            commands = List.copyOf(commands);
        }
    }
}
