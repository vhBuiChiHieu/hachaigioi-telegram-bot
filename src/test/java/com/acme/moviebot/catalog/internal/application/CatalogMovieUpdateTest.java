package com.acme.moviebot.catalog.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.moviebot.catalog.CatalogCommands.UpdateMovieDetailsCommand;
import com.acme.moviebot.catalog.CatalogNotFoundException;
import com.acme.moviebot.catalog.internal.domain.CatalogStatus;
import com.acme.moviebot.catalog.internal.domain.Movie;
import com.acme.moviebot.catalog.internal.persistence.EpisodeRepository;
import com.acme.moviebot.catalog.internal.persistence.MediaAssetRepository;
import com.acme.moviebot.catalog.internal.persistence.MovieRepository;
import com.acme.moviebot.catalog.internal.persistence.SeasonRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CatalogMovieUpdateTest {

    private final MovieRepository movies = mock(MovieRepository.class);
    private final CatalogManagementService catalog = new CatalogManagementService(movies,
            mock(SeasonRepository.class), mock(EpisodeRepository.class), mock(MediaAssetRepository.class));

    @Test
    void renamingRefreshesSearchAndPreservesCoverFullAndPublication() {
        Movie movie = movie();
        when(movies.findById(5L)).thenReturn(Optional.of(movie));

        catalog.updateMovieDetails(new UpdateMovieDetailsCommand(5L, " 教父 ", " Tên Việt mới! ",
                " Mô tả mới.\nGiữ nguyên dòng tiếp theo. "));

        assertThat(movie.getChineseName()).isEqualTo("教父");
        assertThat(movie.getVietnameseName()).isEqualTo("Tên Việt mới!");
        assertThat(movie.getSearchName()).isEqualTo("ten viet moi 教父");
        assertThat(movie.getDescription()).isEqualTo("Mô tả mới.\nGiữ nguyên dòng tiếp theo.");
        assertThat(movie.getThumbnailFileId()).isEqualTo("old-cover");
        assertThat(movie.isFull()).isTrue();
        assertThat(movie.getStatus()).isEqualTo(CatalogStatus.PUBLISHED);
    }

    @Test
    void coverAndFullUpdatesPreserveMetadataAndRepeatedFullActionsAreIdempotent() {
        Movie movie = movie();
        when(movies.findById(5L)).thenReturn(Optional.of(movie));

        catalog.updateMovieThumbnail(5L, "new-cover");
        catalog.setMovieFull(5L, false);
        catalog.setMovieFull(5L, false);
        assertThat(movie.isFull()).isFalse();
        catalog.setMovieFull(5L, true);
        catalog.setMovieFull(5L, true);

        assertThat(movie.isFull()).isTrue();
        assertThat(movie.getThumbnailFileId()).isEqualTo("new-cover");
        assertThat(movie.getVietnameseName()).isEqualTo("Tên cũ");
        assertThat(movie.getChineseName()).isEqualTo("旧名");
        assertThat(movie.getSearchName()).isEqualTo("ten cu 旧名");
        assertThat(movie.getDescription()).isEqualTo("Mô tả cũ");
        assertThat(movie.getStatus()).isEqualTo(CatalogStatus.PUBLISHED);
    }

    @Test
    void invalidMetadataCannotPartiallyChangeTheMovie() {
        Movie movie = movie();
        when(movies.findById(5L)).thenReturn(Optional.of(movie));

        assertThatThrownBy(() -> catalog.updateMovieDetails(new UpdateMovieDetailsCommand(
                5L, "教父", "a".repeat(256), "Mô tả mới"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> catalog.updateMovieDetails(new UpdateMovieDetailsCommand(
                5L, "教父", "Tên mới", " "))).isInstanceOf(IllegalArgumentException.class);

        assertThat(movie.getVietnameseName()).isEqualTo("Tên cũ");
        assertThat(movie.getChineseName()).isEqualTo("旧名");
        assertThat(movie.getDescription()).isEqualTo("Mô tả cũ");
    }

    @Test
    void updatesRequireAnExistingMovie() {
        when(movies.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalog.updateMovieDetails(new UpdateMovieDetailsCommand(
                5L, "教父", "Tên mới", "Mô tả mới"))).isInstanceOf(CatalogNotFoundException.class);
        assertThatThrownBy(() -> catalog.updateMovieThumbnail(5L, "new-cover"))
                .isInstanceOf(CatalogNotFoundException.class);
        assertThatThrownBy(() -> catalog.setMovieFull(5L, true)).isInstanceOf(CatalogNotFoundException.class);
    }

    private Movie movie() {
        Movie movie = new Movie("Tên cũ", "旧名", "ten cu 旧名", "old-cover", "Mô tả cũ", true);
        movie.publish();
        return movie;
    }
}
