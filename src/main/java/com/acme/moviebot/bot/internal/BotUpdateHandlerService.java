package com.acme.moviebot.bot.internal;

import com.acme.moviebot.bot.BotUpdateHandler;
import com.acme.moviebot.bot.BotProcessingException;
import com.acme.moviebot.bot.model.BotAction;
import com.acme.moviebot.bot.model.BotUpdate;
import com.acme.moviebot.catalog.CatalogConflictException;
import com.acme.moviebot.catalog.CatalogNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BotUpdateHandlerService implements BotUpdateHandler {

    private final ProcessedTelegramUpdateRepository processedUpdates;
    private final BotUpdateRouter router;

    public BotUpdateHandlerService(ProcessedTelegramUpdateRepository processedUpdates, BotUpdateRouter router) {
        this.processedUpdates = processedUpdates;
        this.router = router;
    }

    @Override
    @Transactional
    public List<BotAction> handle(BotUpdate update) {
        if (processedUpdates.existsById(update.updateId())) return List.of();
        List<BotAction> actions;
        try {
            actions = router.route(update);
        } catch (CatalogConflictException | CatalogNotFoundException | IllegalArgumentException exception) {
            throw new BotProcessingException(exception.getMessage(), exception);
        }
        processedUpdates.save(new ProcessedTelegramUpdate(update.updateId()));
        return actions;
    }
}
