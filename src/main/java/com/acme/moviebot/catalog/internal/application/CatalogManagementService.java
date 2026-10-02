package com.acme.moviebot.catalog.internal.application;

import com.acme.moviebot.catalog.CatalogCommands.AttachMediaCommand;
import com.acme.moviebot.catalog.CatalogCommands.CreateEpisodeCommand;
import com.acme.moviebot.catalog.CatalogCommands.CreateMovieCommand;
import com.acme.moviebot.catalog.CatalogCommands.CreateSeasonCommand;
import com.acme.moviebot.catalog.CatalogManagement;
import com.acme.moviebot.catalog.CatalogConflictException;
import com.acme.moviebot.catalog.CatalogNotFoundException;
import com.acme.moviebot.catalog.internal.domain.Episode;
import com.acme.moviebot.catalog.internal.domain.CatalogStatus;
import com.acme.moviebot.catalog.internal.domain.MediaAsset;
import com.acme.moviebot.catalog.internal.domain.Movie;
import com.acme.moviebot.catalog.internal.domain.Season;
import com.acme.moviebot.catalog.internal.domain.SearchNormalizer;
import com.acme.moviebot.catalog.internal.persistence.EpisodeRepository;
import com.acme.moviebot.catalog.internal.persistence.MediaAssetRepository;
import com.acme.moviebot.catalog.internal.persistence.MovieRepository;
import com.acme.moviebot.catalog.internal.persistence.SeasonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class CatalogManagementService implements CatalogManagement {

    private final MovieRepository movies;
    private final SeasonRepository seasons;
    private final EpisodeRepository episodes;
    private final MediaAssetRepository mediaAssets;

    public CatalogManagementService(MovieRepository movies, SeasonRepository seasons,
                                    EpisodeRepository episodes, MediaAssetRepository mediaAssets) {
        this.movies = movies;
        this.seasons = seasons;
        this.episodes = episodes;
        this.mediaAssets = mediaAssets;
    }

    @Override
    public long createMovie(CreateMovieCommand command) {
        if (command == null || !StringUtils.hasText(command.vietnameseName())) {
            throw new IllegalArgumentException("Tên phim không được để trống.");
        }
        String vietnameseName = command.vietnameseName().trim();
        String chineseName = clean(command.chineseName());
        String searchName = SearchNormalizer.normalize(vietnameseName + " " + (chineseName == null ? "" : chineseName));
        Movie movie = new Movie(vietnameseName, chineseName, searchName, clean(command.thumbnailFileId()),
                clean(command.description()), command.full());
        return movies.save(movie).getId();
    }

    @Override
    public long createSeason(CreateSeasonCommand command) {
        if (command == null || command.seasonNumber() < 1) {
            throw new IllegalArgumentException("Số season phải lớn hơn hoặc bằng 1.");
        }
        if (command.originalEpisodeCount() < 0) {
            throw new IllegalArgumentException("Tổng số tập gốc phải lớn hơn hoặc bằng 0.");
        }
        Movie movie = movies.findById(command.movieId())
                .orElseThrow(() -> new CatalogNotFoundException("Không tìm thấy phim."));
        if (seasons.existsByMovie_IdAndSeasonNumber(command.movieId(), command.seasonNumber())) {
            throw new CatalogConflictException("Season " + command.seasonNumber() + " đã tồn tại.");
        }
        return seasons.save(new Season(movie, command.seasonNumber(), command.originalEpisodeCount())).getId();
    }

    @Override
    public long createEpisode(CreateEpisodeCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Thiếu thông tin season.");
        }
        Season season = seasons.findById(command.seasonId())
                .orElseThrow(() -> new CatalogNotFoundException("Không tìm thấy season."));
        return episodes.save(new Episode(season)).getId();
    }

    @Override
    public long attachMedia(AttachMediaCommand command) {
        if (command == null || !StringUtils.hasText(command.providerFileId())) {
            throw new IllegalArgumentException("Không nhận được Telegram file_id hợp lệ.");
        }
        Episode episode = episodes.findById(command.episodeId())
                .orElseThrow(() -> new CatalogNotFoundException("Không tìm thấy tập phim."));
        MediaAsset asset = new MediaAsset(episode, command.providerFileId(), command.providerUniqueFileId(),
                command.sourceChatId(), command.sourceMessageId(), command.fileName(), command.mimeType(),
                command.fileSize(), command.durationSeconds(), command.width(), command.height());
        return mediaAssets.save(asset).getId();
    }

    @Override
    public void publishMovie(long movieId) {
        movie(movieId).publish();
    }

    @Override
    public void publishSeason(long seasonId) {
        season(seasonId).publish();
    }

    @Override
    public void archiveSeason(long seasonId) {
        season(seasonId).archive();
    }

    @Override
    public void publishEpisode(long episodeId) {
        Episode episode = episode(episodeId);
        MediaAsset asset = mediaAssets.findFirstByEpisode_IdOrderByIdAsc(episodeId)
                .orElseThrow(() -> new CatalogConflictException("Hãy gửi video trước khi publish tập này."));
        if (episode.getStatus() == CatalogStatus.ARCHIVED) {
            throw new CatalogConflictException("Không thể publish một phần phim đã lưu trữ.");
        }
        asset.publish();
        episode.publish();
    }

    @Override
    public void archiveMovie(long movieId) {
        movie(movieId).archive();
    }

    private Movie movie(long id) {
        return movies.findById(id).orElseThrow(() -> new CatalogNotFoundException("Không tìm thấy phim."));
    }

    private Season season(long id) {
        return seasons.findById(id).orElseThrow(() -> new CatalogNotFoundException("Không tìm thấy season."));
    }

    private Episode episode(long id) {
        return episodes.findById(id).orElseThrow(() -> new CatalogNotFoundException("Không tìm thấy tập phim."));
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
