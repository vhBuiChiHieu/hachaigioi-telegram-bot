package com.acme.moviebot.catalog;

public final class CatalogViews {

    private CatalogViews() {
    }

    public record MovieSummary(long id, String vietnameseName, String chineseName) {
    }

    public record MovieDetails(
            long id,
            String vietnameseName,
            String chineseName,
            String thumbnailFileId,
            String description,
            boolean full,
            String status) {
    }

    public record SeasonSummary(long id, long movieId, int seasonNumber, int originalEpisodeCount) {
    }

    public record EpisodeSummary(long id, long seasonId, int partNumber) {
    }

    public record EpisodeMediaView(
            long episodeId,
            String movieName,
            int seasonNumber,
            int partNumber,
            String providerFileId) {
    }
}
