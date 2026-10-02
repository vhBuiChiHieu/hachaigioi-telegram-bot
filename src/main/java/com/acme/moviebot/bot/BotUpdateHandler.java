package com.acme.moviebot.bot;

import com.acme.moviebot.bot.model.BotAction;
import com.acme.moviebot.bot.model.BotUpdate;
import java.util.List;

public interface BotUpdateHandler {

    List<BotAction> handle(BotUpdate update);
}
