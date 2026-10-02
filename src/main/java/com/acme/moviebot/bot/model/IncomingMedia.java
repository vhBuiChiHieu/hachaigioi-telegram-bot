package com.acme.moviebot.bot.model;

public record IncomingMedia(
        String fileId,
        String fileUniqueId,
        String fileName,
        String mimeType,
        Long fileSize,
        Integer durationSeconds,
        Integer width,
        Integer height) {
}
