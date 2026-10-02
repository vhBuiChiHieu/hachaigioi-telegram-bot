package com.acme.moviebot.access;

import java.util.Set;

public interface AccessControl {

    boolean isAdmin(long telegramUserId);

    Set<Long> adminIds();

    default boolean isAllowed(long telegramUserId) {
        return telegramUserId > 0;
    }
}
