package com.acme.moviebot.telegram.internal.client;

import com.fasterxml.jackson.databind.JsonNode;

public record TelegramUpdateDto(long updateId, JsonNode payload) {
}
