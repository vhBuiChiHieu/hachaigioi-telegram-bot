package com.acme.moviebot.catalog;

public final class CatalogViews {

    private CatalogViews() {
    }

    public record MovieSummary(long id, String slug, String name) {
    }

    public record MovieDetails(long id, String slug, String name, String originalName, String description, String status) {
    }

    public record SeasonSummary(long id, long movieId, int seasonNumber, String name) {
    }

    public record EpisodeSummary(long id, long seasonId, int episodeNumber, String name, String description) {
    }

    public record EpisodeMediaView(
            long episodeId,
            String movieName,
            int seasonNumber,
            int episodeNumber,
            String episodeName,
            String providerFileId) {
    }
}
