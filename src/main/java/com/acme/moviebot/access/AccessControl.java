package com.acme.moviebot.access;

public interface AccessControl {

    boolean isAdmin(long telegramUserId);

    default boolean isAllowed(long telegramUserId) {
        return telegramUserId > 0;
    }
}
