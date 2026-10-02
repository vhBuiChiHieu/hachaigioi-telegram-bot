package com.acme.moviebot.access;

public record PrincipalView(long telegramUserId, String role, boolean enabled) {
}
