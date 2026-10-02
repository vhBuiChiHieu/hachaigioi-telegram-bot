package com.acme.moviebot.catalog.internal.application;

import com.acme.moviebot.catalog.CatalogQuery;
import com.acme.moviebot.catalog.CatalogViews.EpisodeMediaView;
import com.acme.moviebot.catalog.CatalogViews.EpisodeSummary;
import com.acme.moviebot.catalog.CatalogViews.MovieDetails;
import com.acme.moviebot.catalog.CatalogViews.MovieSummary;
import com.acme.moviebot.catalog.CatalogViews.SeasonSummary;
import com.acme.moviebot.catalog.internal.domain.Episode;
import com.acme.moviebot.catalog.internal.domain.EpisodeStatus;
import com.acme.moviebot.catalog.internal.domain.MediaAsset;
import com.acme.moviebot.catalog.internal.domain.Movie;
import com.acme.moviebot.catalog.internal.domain.MovieStatus;
import com.acme.moviebot.catalog.internal.domain.Season;
import com.acme.moviebot.catalog.internal.domain.SeasonStatus;
import com.acme.moviebot.catalog.internal.domain.SearchNormalizer;
import com.acme.moviebot.catalog.internal.persistence.EpisodeRepository;
import com.acme.moviebot.catalog.internal.persistence.MediaAssetRepository;
import com.acme.moviebot.catalog.internal.persistence.MovieRepository;
import com.acme.moviebot.catalog.internal.persistence.SeasonRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CatalogQueryService implements CatalogQuery {

    private final MovieRepository movies;
    private final SeasonRepository seasons;
    private final EpisodeRepository episodes;
    private final MediaAssetRepository mediaAssets;

    public CatalogQueryService(MovieRepository movies, SeasonRepository seasons,
                               EpisodeRepository episodes, MediaAssetRepository mediaAssets) {
        this.movies = movies;
        this.seasons = seasons;
        this.episodes = episodes;
        this.mediaAssets = mediaAssets;
    }

    @Override
    public List<MovieSummary> searchMovies(String keyword, int limit) {
        String normalized = SearchNormalizer.normalize(keyword);
        if (normalized.isBlank() || limit < 1) {
            return List.of();
        }
        int boundedLimit = Math.min(limit, 50);
        return movies.searchPublished(MovieStatus.PUBLISHED, normalized, PageRequest.of(0, boundedLimit))
                .stream().map(movie -> new MovieSummary(movie.getId(), movie.getSlug(), movie.getName())).toList();
    }

    @Override
    public List<MovieDetails> searchMoviesForAdmin(String keyword, int limit) {
        String normalized = SearchNormalizer.normalize(keyword);
        if (normalized.isBlank() || limit < 1) return List.of();
        int boundedLimit = Math.min(limit, 50);
        return movies.searchForAdmin(MovieStatus.ARCHIVED, normalized, PageRequest.of(0, boundedLimit))
                .stream().map(this::toDetails).toList();
    }

    @Override
    public Optional<MovieDetails> findMovie(long movieId) {
        return movies.findById(movieId).map(this::toDetails);
    }

    @Override
    public List<SeasonSummary> findSeasonsForAdmin(long movieId) {
        return seasons.findByMovie_IdOrderBySeasonNumberAsc(movieId).stream().map(this::toSeasonSummary).toList();
    }

    @Override
    public List<SeasonSummary> findPublishedSeasons(long movieId) {
        return movies.findById(movieId)
                .filter(movie -> movie.getStatus() == MovieStatus.PUBLISHED)
                .map(movie -> seasons.findByMovie_IdAndStatusOrderBySeasonNumberAsc(movieId, SeasonStatus.PUBLISHED)
                        .stream().map(this::toSeasonSummary).toList())
                .orElseGet(List::of);
    }

    @Override
    public List<EpisodeSummary> findPublishedEpisodes(long seasonId) {
        return seasons.findById(seasonId)
                .filter(season -> season.getStatus() == SeasonStatus.PUBLISHED)
                .filter(season -> season.getMovie().getStatus() == MovieStatus.PUBLISHED)
                .map(season -> episodes.findBySeason_IdAndStatusOrderByEpisodeNumberAsc(seasonId, EpisodeStatus.PUBLISHED)
                        .stream().map(this::toEpisodeSummary).toList())
                .orElseGet(List::of);
    }

    @Override
    public Optional<EpisodeMediaView> findEpisodeMedia(long episodeId) {
        return episodes.findByIdAndStatus(episodeId, EpisodeStatus.PUBLISHED)
                .filter(episode -> episode.getSeason().getStatus() == SeasonStatus.PUBLISHED)
                .filter(episode -> episode.getSeason().getMovie().getStatus() == MovieStatus.PUBLISHED)
                .flatMap(episode -> mediaAssets.findFirstByEpisode_IdOrderByIdAsc(episodeId)
                        .map(asset -> toEpisodeMediaView(episode, asset)));
    }

    private MovieDetails toDetails(Movie movie) {
        return new MovieDetails(movie.getId(), movie.getSlug(), movie.getName(), movie.getOriginalName(),
                movie.getDescription(), movie.getStatus().name());
    }

    private SeasonSummary toSeasonSummary(Season season) {
        return new SeasonSummary(season.getId(), season.getMovie().getId(), season.getSeasonNumber(), season.getName());
    }

    private EpisodeSummary toEpisodeSummary(Episode episode) {
        return new EpisodeSummary(episode.getId(), episode.getSeason().getId(), episode.getEpisodeNumber(),
                episode.getName(), episode.getDescription());
    }

    private EpisodeMediaView toEpisodeMediaView(Episode episode, MediaAsset asset) {
        Season season = episode.getSeason();
        Movie movie = season.getMovie();
        return new EpisodeMediaView(episode.getId(), movie.getName(), season.getSeasonNumber(), episode.getEpisodeNumber(),
                episode.getName(), asset.getProviderFileId());
    }
}
