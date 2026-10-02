package com.acme.moviebot.catalog;

import com.acme.moviebot.catalog.CatalogViews.EpisodeMediaView;
import com.acme.moviebot.catalog.CatalogViews.EpisodeDetails;
import com.acme.moviebot.catalog.CatalogViews.EpisodeSummary;
import com.acme.moviebot.catalog.CatalogViews.MovieDetails;
import com.acme.moviebot.catalog.CatalogViews.MoviePage;
import com.acme.moviebot.catalog.CatalogViews.MovieSummary;
import com.acme.moviebot.catalog.CatalogViews.SeasonSummary;
import com.acme.moviebot.catalog.CatalogViews.SeasonDetails;
import java.util.List;
import java.util.Optional;

public interface CatalogQuery {

    List<MovieSummary> searchMovies(String keyword, int limit);

    List<MovieDetails> searchMoviesForAdmin(String keyword, int limit);

    MoviePage findMoviesForAdmin(int page, int size);

    Optional<MovieDetails> findMovie(long movieId);

    List<SeasonDetails> findSeasonsForAdmin(long movieId);

    Optional<SeasonDetails> findSeasonForAdmin(long seasonId);

    List<EpisodeDetails> findEpisodesForAdmin(long seasonId);

    List<SeasonSummary> findPublishedSeasons(long movieId);

    List<EpisodeSummary> findPublishedEpisodes(long seasonId);

    Optional<EpisodeMediaView> findEpisodeMedia(long episodeId);
}
