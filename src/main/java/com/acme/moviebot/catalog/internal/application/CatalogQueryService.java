package com.acme.moviebot.catalog.internal.application;

import com.acme.moviebot.catalog.CatalogQuery;
import com.acme.moviebot.catalog.CatalogViews.EpisodeMediaView;
import com.acme.moviebot.catalog.CatalogViews.EpisodeDetails;
import com.acme.moviebot.catalog.CatalogViews.EpisodeSummary;
import com.acme.moviebot.catalog.CatalogViews.MovieDetails;
import com.acme.moviebot.catalog.CatalogViews.MoviePage;
import com.acme.moviebot.catalog.CatalogViews.MovieSummary;
import com.acme.moviebot.catalog.CatalogViews.SeasonSummary;
import com.acme.moviebot.catalog.CatalogViews.SeasonDetails;
import com.acme.moviebot.catalog.internal.domain.Episode;
import com.acme.moviebot.catalog.internal.domain.CatalogStatus;
import com.acme.moviebot.catalog.internal.domain.MediaAsset;
import com.acme.moviebot.catalog.internal.domain.Movie;
import com.acme.moviebot.catalog.internal.domain.Season;
import com.acme.moviebot.catalog.internal.domain.FuzzySearchMatcher;
import com.acme.moviebot.catalog.internal.persistence.EpisodeRepository;
import com.acme.moviebot.catalog.internal.persistence.MediaAssetRepository;
import com.acme.moviebot.catalog.internal.persistence.MovieRepository;
import com.acme.moviebot.catalog.internal.persistence.SeasonRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
        return fuzzyMatches(movies.findByStatusOrderByVietnameseNameAsc(CatalogStatus.PUBLISHED), keyword, limit)
                .stream().map(movie -> new MovieSummary(movie.getId(), movie.getVietnameseName(), movie.getChineseName())).toList();
    }

    @Override
    public List<MovieDetails> searchMoviesForAdmin(String keyword, int limit) {
        return fuzzyMatches(movies.findByStatusNotOrderByVietnameseNameAsc(CatalogStatus.ARCHIVED), keyword, limit)
                .stream().map(this::toDetails).toList();
    }

    @Override
    public MoviePage findMoviesForAdmin(int page, int size) {
        if (page < 0 || size < 1) {
            throw new IllegalArgumentException("Số trang và kích thước trang phải hợp lệ.");
        }
        var result = movies.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        return new MoviePage(result.getContent().stream().map(this::toDetails).toList(), page,
                result.getTotalPages(), result.hasNext());
    }

    private List<Movie> fuzzyMatches(List<Movie> candidates, String keyword, int limit) {
        if (keyword == null || keyword.isBlank() || limit < 1) {
            return List.of();
        }
        int boundedLimit = Math.min(limit, 50);
        return candidates.stream()
                .map(movie -> new MovieMatch(movie, FuzzySearchMatcher.score(keyword, searchText(movie))))
                .filter(match -> match.score() >= FuzzySearchMatcher.MIN_SCORE)
                .sorted(Comparator.comparingDouble(MovieMatch::score).reversed()
                        .thenComparing(match -> match.movie().getVietnameseName(), String.CASE_INSENSITIVE_ORDER))
                .limit(boundedLimit)
                .map(MovieMatch::movie)
                .toList();
    }

    private String searchText(Movie movie) {
        return movie.getVietnameseName() + " " + (movie.getChineseName() == null ? "" : movie.getChineseName())
                + " " + movie.getSearchName();
    }

    private record MovieMatch(Movie movie, double score) {
    }

    @Override
    public Optional<MovieDetails> findMovie(long movieId) {
        return movies.findById(movieId).map(this::toDetails);
    }

    @Override
    public List<SeasonDetails> findSeasonsForAdmin(long movieId) {
        return seasons.findByMovie_IdOrderBySeasonNumberAsc(movieId).stream().map(this::toSeasonDetails).toList();
    }

    @Override
    public Optional<SeasonDetails> findSeasonForAdmin(long seasonId) {
        return seasons.findById(seasonId).map(this::toSeasonDetails);
    }

    @Override
    public List<EpisodeDetails> findEpisodesForAdmin(long seasonId) {
        List<Episode> parts = episodes.findBySeason_IdOrderByIdAsc(seasonId);
        return java.util.stream.IntStream.range(0, parts.size())
                .mapToObj(index -> toEpisodeDetails(parts.get(index), index + 1))
                .toList();
    }

    @Override
    public Optional<EpisodeDetails> findEpisodeForAdmin(long episodeId) {
        return episodes.findById(episodeId).map(episode -> toEpisodeDetails(episode,
                Math.toIntExact(episodes.countBySeason_IdAndIdLessThanEqual(episode.getSeason().getId(), episode.getId()))));
    }

    @Override
    public Optional<EpisodeMediaView> findEpisodeMediaForAdmin(long episodeId) {
        return episodes.findById(episodeId)
                .flatMap(episode -> mediaAssets.findFirstByEpisode_IdOrderByIdAsc(episodeId)
                        .map(asset -> toEpisodeMediaView(episode, asset)));
    }

    @Override
    public List<SeasonSummary> findPublishedSeasons(long movieId) {
        return movies.findById(movieId)
                .filter(movie -> movie.getStatus() == CatalogStatus.PUBLISHED)
                .map(movie -> seasons.findByMovie_IdAndStatusOrderBySeasonNumberAsc(movieId, CatalogStatus.PUBLISHED)
                        .stream().map(this::toSeasonSummary).toList())
                .orElseGet(List::of);
    }

    @Override
    public List<EpisodeSummary> findPublishedEpisodes(long seasonId) {
        return seasons.findById(seasonId)
                .filter(season -> season.getStatus() == CatalogStatus.PUBLISHED)
                .filter(season -> season.getMovie().getStatus() == CatalogStatus.PUBLISHED)
                .map(season -> {
                    List<Episode> parts = episodes.findBySeason_IdOrderByIdAsc(seasonId);
                    return java.util.stream.IntStream.range(0, parts.size())
                            .filter(index -> parts.get(index).getStatus() == CatalogStatus.PUBLISHED)
                            .mapToObj(index -> toEpisodeSummary(parts.get(index), index + 1))
                            .toList();
                })
                .orElseGet(List::of);
    }

    @Override
    public Optional<EpisodeMediaView> findEpisodeMedia(long episodeId) {
        return episodes.findByIdAndStatus(episodeId, CatalogStatus.PUBLISHED)
                .filter(episode -> episode.getSeason().getStatus() == CatalogStatus.PUBLISHED)
                .filter(episode -> episode.getSeason().getMovie().getStatus() == CatalogStatus.PUBLISHED)
                .flatMap(episode -> mediaAssets.findFirstByEpisode_IdAndStatusOrderByIdAsc(episodeId, CatalogStatus.PUBLISHED)
                        .map(asset -> toEpisodeMediaView(episode, asset)));
    }

    private MovieDetails toDetails(Movie movie) {
        return new MovieDetails(movie.getId(), movie.getVietnameseName(), movie.getChineseName(),
                movie.getThumbnailFileId(), movie.getDescription(), movie.isFull(), movie.getStatus().name());
    }

    private SeasonSummary toSeasonSummary(Season season) {
        return new SeasonSummary(season.getId(), season.getMovie().getId(), season.getSeasonNumber(),
                season.getOriginalEpisodeCount());
    }

    private SeasonDetails toSeasonDetails(Season season) {
        return new SeasonDetails(season.getId(), season.getMovie().getId(), season.getSeasonNumber(),
                season.getOriginalEpisodeCount(), season.getStatus().name());
    }

    private EpisodeSummary toEpisodeSummary(Episode episode, int partNumber) {
        return new EpisodeSummary(episode.getId(), episode.getSeason().getId(), partNumber);
    }

    private EpisodeDetails toEpisodeDetails(Episode episode, int partNumber) {
        return new EpisodeDetails(episode.getId(), episode.getSeason().getId(), partNumber, episode.getStatus().name());
    }

    private EpisodeMediaView toEpisodeMediaView(Episode episode, MediaAsset asset) {
        Season season = episode.getSeason();
        Movie movie = season.getMovie();
        int partNumber = Math.toIntExact(episodes.countBySeason_IdAndIdLessThanEqual(season.getId(), episode.getId()));
        return new EpisodeMediaView(episode.getId(), movie.getVietnameseName(), season.getSeasonNumber(),
                partNumber, asset.getProviderFileId());
    }
}
