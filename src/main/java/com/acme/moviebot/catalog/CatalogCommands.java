package com.acme.moviebot.catalog;

import com.acme.moviebot.catalog.internal.domain.ExternalLink;

public final class CatalogCommands {

    private CatalogCommands() {
    }

    public record CreateMovieCommand(
            String vietnameseName,
            String chineseName,
            String thumbnailFileId,
            String description,
            boolean full) {
    }

    public record CreateSeasonCommand(long movieId, int seasonNumber, int originalEpisodeCount) {
    }

    public record UpdateMovieDetailsCommand(long movieId, String chineseName, String vietnameseName, String description) {
    }

    public record CreateEpisodeCommand(long seasonId) {
    }

    public record AttachLinkCommand(long episodeId, String externalUrl) {
        public AttachLinkCommand {
            externalUrl = new ExternalLink(externalUrl).url();
        }
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
