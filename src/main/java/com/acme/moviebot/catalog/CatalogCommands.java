package com.acme.moviebot.catalog;

public final class CatalogCommands {

    private CatalogCommands() {
    }

    public record CreateMovieCommand(String name, String originalName, String description) {
    }

    public record CreateSeasonCommand(long movieId, int seasonNumber, String name) {
    }

    public record CreateEpisodeCommand(long seasonId, int episodeNumber, String name, String description) {
    }

    public record AttachMediaCommand(
            long episodeId,
            String providerFileId,
            String providerUniqueFileId,
            long sourceChatId,
            long sourceMessageId,
            String fileName,
            String mimeType,
            Long fileSize,
            Integer durationSeconds,
            Integer width,
            Integer height) {
    }
}
