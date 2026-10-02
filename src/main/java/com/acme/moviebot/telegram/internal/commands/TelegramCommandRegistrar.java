package com.acme.moviebot.telegram.internal.commands;

import com.acme.moviebot.bot.BotCommandMenus;
import com.acme.moviebot.telegram.internal.client.TelegramBotClient;
import com.acme.moviebot.telegram.internal.client.TelegramBotCommand;
import com.acme.moviebot.telegram.internal.config.TelegramProperties;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class TelegramCommandRegistrar implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TelegramCommandRegistrar.class);

    private final TelegramBotClient telegram;
    private final TelegramProperties properties;
    private final BotCommandMenus commandMenus;

    public TelegramCommandRegistrar(TelegramBotClient telegram, TelegramProperties properties,
                                    BotCommandMenus commandMenus) {
        this.telegram = telegram;
        this.properties = properties;
        this.commandMenus = commandMenus;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.botToken().isBlank()) {
            log.info("telegram.commands.not_registered reason=missing_bot_token");
            return;
        }

        register("all_private_chats", () -> telegram.setMyCommands(
                toTelegramCommands(commandMenus.userCommands()), Map.of("type", "all_private_chats")));

        for (BotCommandMenus.AdminMenu adminMenu : commandMenus.adminMenus()) {
            register("admin_chat", () -> telegram.setMyCommands(
                    toTelegramCommands(adminMenu.commands()),
                    Map.of("type", "chat", "chat_id", adminMenu.telegramUserId())));
        }
    }

    private List<TelegramBotCommand> toTelegramCommands(List<BotCommandMenus.Command> commands) {
        return commands.stream()
                .map(command -> new TelegramBotCommand(command.command(), command.description()))
                .toList();
    }

    private void register(String scope, Runnable registration) {
        try {
            registration.run();
        } catch (RuntimeException exception) {
            log.warn("telegram.commands.registration_failed scope={} cause={}", scope,
                    exception.getClass().getSimpleName());
        }
    }
}
