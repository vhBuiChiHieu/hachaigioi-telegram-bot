package com.acme.moviebot.telegram.internal.polling;

import com.acme.moviebot.bot.BotUpdateHandler;
import com.acme.moviebot.bot.BotProcessingException;
import com.acme.moviebot.bot.model.BotAction;
import com.acme.moviebot.bot.model.BotUpdate;
import com.acme.moviebot.bot.model.AnswerCallbackAction;
import com.acme.moviebot.bot.model.CallbackUpdate;
import com.acme.moviebot.bot.model.SendTextAction;
import com.acme.moviebot.telegram.internal.client.TelegramUpdateDto;
import com.acme.moviebot.telegram.internal.executor.TelegramActionExecutor;
import com.acme.moviebot.telegram.internal.mapper.TelegramUpdateMapper;
import java.util.List;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TelegramUpdateProcessor {

    private static final Logger log = LoggerFactory.getLogger(TelegramUpdateProcessor.class);
    private final TelegramUpdateMapper mapper;
    private final BotUpdateHandler handler;
    private final TelegramActionExecutor executor;

    public TelegramUpdateProcessor(TelegramUpdateMapper mapper, BotUpdateHandler handler, TelegramActionExecutor executor) {
        this.mapper = mapper;
        this.handler = handler;
        this.executor = executor;
    }

    public void process(TelegramUpdateDto update) {
        var internal = mapper.map(update);
        if (internal.isEmpty()) {
            log.debug("telegram.update.ignored updateId={} reason=unsupported", update.updateId());
            return;
        }
        BotUpdate botUpdate = internal.get();
        List<BotAction> actions;
        try {
            actions = handler.handle(botUpdate);
        } catch (BotProcessingException exception) {
            actions = errorActions(botUpdate, exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("telegram.update.failed updateId={} telegramUserId={}", update.updateId(), botUpdate.userId(), exception);
            actions = errorActions(botUpdate, "Có lỗi khi xử lý yêu cầu. Vui lòng thử lại sau.");
        }
        for (BotAction action : actions) {
            try {
                executor.execute(action);
            } catch (RuntimeException exception) {
                log.error("telegram.action.failed updateId={} actionType={} error={}", update.updateId(),
                        action.getClass().getSimpleName(), exception.getMessage());
            }
        }
        log.info("telegram.update.processed updateId={} telegramUserId={} type={}", update.updateId(),
                botUpdate.userId(), botUpdate.getClass().getSimpleName());
    }

    private List<BotAction> errorActions(BotUpdate update, String message) {
        List<BotAction> actions = new ArrayList<>();
        if (update instanceof CallbackUpdate callback) {
            actions.add(new AnswerCallbackAction(callback.callbackQueryId(), "", false));
        }
        actions.add(new SendTextAction(update.chatId(), message));
        return actions;
    }
}
