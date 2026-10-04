package com.acme.moviebot.bot.internal;

import com.acme.moviebot.bot.model.BotAction;
import com.acme.moviebot.bot.model.SendTextAction;
import com.acme.moviebot.bot.model.SendVideoAction;
import com.acme.moviebot.catalog.CatalogViews.EpisodeMediaView;

public final class EpisodeContentPresenter {

    private EpisodeContentPresenter() {
    }

    public static BotAction present(long chatId, EpisodeMediaView media) {
        String title = media.movieName() + " - Mùa " + media.seasonNumber() + " - Phần " + media.partNumber();
        return switch (media.mediaType()) {
            case VIDEO -> new SendVideoAction(chatId, media.providerFileId(), title);
            case LINK -> new SendTextAction(chatId, title + "\n" + media.externalUrl());
        };
    }
}
