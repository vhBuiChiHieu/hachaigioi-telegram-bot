package com.acme.moviebot.bot.internal;

import com.acme.moviebot.access.AccessControl;
import com.acme.moviebot.bot.internal.CallbackDataCodec.DecodedCallback;
import com.acme.moviebot.bot.internal.admin.AdminCommandHandler;
import com.acme.moviebot.bot.internal.user.UserCommandHandler;
import com.acme.moviebot.bot.model.AnswerCallbackAction;
import com.acme.moviebot.bot.model.BotAction;
import com.acme.moviebot.bot.model.BotUpdate;
import com.acme.moviebot.bot.model.CallbackUpdate;
import com.acme.moviebot.bot.model.MediaMessageUpdate;
import com.acme.moviebot.bot.model.SendTextAction;
import com.acme.moviebot.bot.model.TextMessageUpdate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class BotUpdateRouter {

    private final AccessControl accessControl;
    private final AdminCommandHandler admins;
    private final UserCommandHandler users;
    private final CallbackDataCodec callbacks;

    public BotUpdateRouter(AccessControl accessControl, AdminCommandHandler admins,
                           UserCommandHandler users, CallbackDataCodec callbacks) {
        this.accessControl = accessControl;
        this.admins = admins;
        this.users = users;
        this.callbacks = callbacks;
    }

    public List<BotAction> route(BotUpdate update) {
        if (update instanceof TextMessageUpdate text) return routeText(text);
        if (update instanceof MediaMessageUpdate media) return admins.handleMedia(media);
        if (update instanceof CallbackUpdate callback) return routeCallback(callback);
        return List.of();
    }

    private List<BotAction> routeText(TextMessageUpdate update) {
        String value = update.text() == null ? "" : update.text().trim();
        String[] pieces = value.split("\\s+", 2);
        String command = pieces[0].split("@", 2)[0].toLowerCase(Locale.ROOT);
        String argument = pieces.length > 1 ? pieces[1].trim() : "";
        if ("/admin".equals(command)) return admins.entry(update.userId(), update.chatId());
        if ("/cancel".equals(command)) {
            if (!accessControl.isAdmin(update.userId())) {
                return List.of(new SendTextAction(update.chatId(), "Bạn không có quyền thực hiện thao tác quản trị này."));
            }
            return admins.handleCallback(new CallbackUpdate(update.updateId(), update.userId(), update.chatId(), "", "a:cancel"), "cancel", null);
        }
        if (admins.hasSession(update.userId(), update.chatId())) return admins.handleText(update);
        return users.handleCommand(update, command, argument);
    }

    private List<BotAction> routeCallback(CallbackUpdate update) {
        DecodedCallback decoded;
        try {
            decoded = callbacks.decode(update.data());
        } catch (RuntimeException exception) {
            return List.of(new AnswerCallbackAction(update.callbackQueryId(), "", false),
                    new SendTextAction(update.chatId(), "Lựa chọn không hợp lệ hoặc đã hết hạn."));
        }
        if (decoded.type().startsWith("a:")) {
            Long id;
            try {
                id = decoded.arguments().isEmpty() ? null : decoded.longArgument(0);
            } catch (RuntimeException exception) {
                return List.of(new AnswerCallbackAction(update.callbackQueryId(), "", false),
                        new SendTextAction(update.chatId(), "Lựa chọn không hợp lệ hoặc đã hết hạn."));
            }
            List<BotAction> actions = new ArrayList<>();
            actions.add(new AnswerCallbackAction(update.callbackQueryId(), "", false));
            actions.addAll(admins.handleCallback(update, decoded.type().substring(2), id));
            return actions;
        }
        return users.handleCallback(update, decoded);
    }
}
