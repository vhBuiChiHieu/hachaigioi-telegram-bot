package com.acme.moviebot.telegram.internal.executor;

import com.acme.moviebot.bot.model.AnswerCallbackAction;
import com.acme.moviebot.bot.model.BotAction;
import com.acme.moviebot.bot.model.EditMessageAction;
import com.acme.moviebot.bot.model.InlineButton;
import com.acme.moviebot.bot.model.SendPhotoAction;
import com.acme.moviebot.bot.model.SendTextAction;
import com.acme.moviebot.bot.model.SendVideoAction;
import com.acme.moviebot.telegram.internal.client.TelegramApiException;
import com.acme.moviebot.telegram.internal.client.TelegramBotClient;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TelegramActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(TelegramActionExecutor.class);
    private static final int MAX_ATTEMPTS = 3;
    private final TelegramBotClient client;

    public TelegramActionExecutor(TelegramBotClient client) {
        this.client = client;
    }

    public void execute(BotAction action) {
        for (int attempt = 1; ; attempt++) {
            try {
                executeOnce(action);
                return;
            } catch (TelegramApiException exception) {
                if (!exception.isTransientFailure() || attempt >= MAX_ATTEMPTS) throw exception;
                long delay = exception.retryAfterSeconds() == null ? 200L * attempt : exception.retryAfterSeconds() * 1000L;
                sleep(Math.min(delay, 5_000L));
            }
        }
    }

    private void executeOnce(BotAction action) {
        if (action instanceof SendTextAction send) {
            client.sendMessage(send.chatId(), send.text(), keyboard(send.keyboard()));
        } else if (action instanceof SendPhotoAction send) {
            client.sendPhoto(send.chatId(), send.fileId(), send.caption());
        } else if (action instanceof SendVideoAction send) {
            client.sendVideo(send.chatId(), send.fileId(), send.caption());
        } else if (action instanceof EditMessageAction edit) {
            client.editMessageText(edit.chatId(), edit.messageId(), edit.text(), keyboard(edit.keyboard()));
        } else if (action instanceof AnswerCallbackAction answer) {
            client.answerCallbackQuery(answer.callbackQueryId(), answer.text(), answer.showAlert());
        } else {
            log.warn("Unsupported bot action type={}", action.getClass().getName());
        }
    }

    private List<List<Map<String, String>>> keyboard(List<List<InlineButton>> rows) {
        if (rows == null || rows.isEmpty()) return List.of();
        return rows.stream().map(row -> row.stream()
                .map(button -> Map.of("text", button.text(), "callback_data", button.callbackData()))
                .toList()).toList();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TelegramApiException("Interrupted while waiting to retry Telegram request.");
        }
    }
}
