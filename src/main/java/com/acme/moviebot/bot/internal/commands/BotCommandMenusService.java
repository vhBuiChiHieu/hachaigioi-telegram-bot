package com.acme.moviebot.bot.internal.commands;

import com.acme.moviebot.access.AccessControl;
import com.acme.moviebot.bot.BotCommandMenus;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class BotCommandMenusService implements BotCommandMenus {

    private static final List<Command> USER_COMMANDS = List.of(
            new Command("menu", "Mở menu chức năng"),
            new Command("help", "Xem hướng dẫn"),
            new Command("find", "Tìm phim theo tên"));

    private static final List<Command> ADMIN_COMMANDS = List.of(
            new Command("admin", "Mở bảng quản trị"),
            new Command("cancel", "Hủy thao tác quản trị"));

    private final AccessControl accessControl;

    public BotCommandMenusService(AccessControl accessControl) {
        this.accessControl = accessControl;
    }

    @Override
    public List<Command> userCommands() {
        return USER_COMMANDS;
    }

    @Override
    public List<AdminMenu> adminMenus() {
        List<Command> allCommands = Stream.concat(USER_COMMANDS.stream(), ADMIN_COMMANDS.stream()).toList();
        return accessControl.adminIds().stream()
                .sorted()
                .map(adminId -> new AdminMenu(adminId, allCommands))
                .toList();
    }
}
