package com.acme.moviebot.catalog;

import java.util.List;

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

    public record MoviePage(List<MovieDetails> movies, int page, int totalPages, boolean hasNext) {
        public MoviePage {
            movies = List.copyOf(movies);
        }
    }

    public record SeasonSummary(long id, long movieId, int seasonNumber, int originalEpisodeCount) {
    }

    public record SeasonDetails(long id, long movieId, int seasonNumber, int originalEpisodeCount, String status) {
    }

    public record EpisodeSummary(long id, long seasonId, int partNumber) {
    }

    public record EpisodeDetails(long id, long seasonId, int partNumber, String status) {
    }

    public enum EpisodeMediaType {
        VIDEO,
        LINK
    }

    public record EpisodeMediaView(
            long episodeId,
            String movieName,
            int seasonNumber,
            int partNumber,
            EpisodeMediaType mediaType,
            String providerFileId,
            String externalUrl) {
    }
}
