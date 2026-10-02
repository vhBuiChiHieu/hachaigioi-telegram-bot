package com.acme.moviebot.catalog;

import com.acme.moviebot.catalog.CatalogViews.EpisodeMediaView;
import com.acme.moviebot.catalog.CatalogViews.EpisodeSummary;
import com.acme.moviebot.catalog.CatalogViews.MovieDetails;
import com.acme.moviebot.catalog.CatalogViews.MovieSummary;
import com.acme.moviebot.catalog.CatalogViews.SeasonSummary;
import java.util.List;
import java.util.Optional;

public interface CatalogQuery {

    List<MovieSummary> searchMovies(String keyword, int limit);

    List<MovieDetails> searchMoviesForAdmin(String keyword, int limit);

    Optional<MovieDetails> findMovie(long movieId);

    List<SeasonSummary> findSeasonsForAdmin(long movieId);

    List<SeasonSummary> findPublishedSeasons(long movieId);

    List<EpisodeSummary> findPublishedEpisodes(long seasonId);

    Optional<EpisodeMediaView> findEpisodeMedia(long episodeId);
}
