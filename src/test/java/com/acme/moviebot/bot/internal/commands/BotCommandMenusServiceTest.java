package com.acme.moviebot.bot.internal.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.moviebot.access.AccessControl;
import com.acme.moviebot.bot.BotCommandMenus.Command;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BotCommandMenusServiceTest {

    @Test
    void userAndAdminCommandMenusAdvertiseMenuInsteadOfStart() {
        AccessControl access = mock(AccessControl.class);
        when(access.adminIds()).thenReturn(Set.of(7L));
        BotCommandMenusService menus = new BotCommandMenusService(access);

        assertThat(menus.userCommands()).extracting(Command::command)
                .containsExactly("menu", "help", "find");
        assertThat(menus.adminMenus()).hasSize(1);
        assertThat(menus.adminMenus().getFirst().commands()).extracting(Command::command)
                .containsExactly("menu", "help", "find", "admin", "cancel");
    }
}
