package com.acme.moviebot.catalog.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.moviebot.catalog.CatalogConflictException;
import com.acme.moviebot.catalog.internal.domain.CatalogStatus;
import com.acme.moviebot.catalog.internal.domain.Episode;
import com.acme.moviebot.catalog.internal.domain.MediaAsset;
import com.acme.moviebot.catalog.internal.domain.Movie;
import com.acme.moviebot.catalog.internal.domain.Season;
import com.acme.moviebot.catalog.internal.persistence.EpisodeRepository;
import com.acme.moviebot.catalog.internal.persistence.MediaAssetRepository;
import com.acme.moviebot.catalog.internal.persistence.MovieRepository;
import com.acme.moviebot.catalog.internal.persistence.SeasonRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CatalogEpisodePublicationTest {

    private final EpisodeRepository episodes = mock(EpisodeRepository.class);
    private final MediaAssetRepository mediaAssets = mock(MediaAssetRepository.class);
    private final Movie movie = new Movie("Phim", null, "phim", null, null, false);
    private final Season season = new Season(movie, 1, 10);
    private final Episode episode = new Episode(season);
    private final MediaAsset media = new MediaAsset(episode, "video", null, 7L, 99L, null, "video/mp4", null, null, null, null);
    private final CatalogManagementService catalog = new CatalogManagementService(
            mock(MovieRepository.class), mock(SeasonRepository.class), episodes, mediaAssets);

    @Test
    void publicationChangesThePartAndItsVideoTogetherAndPreservesParentStatuses() {
        movie.publish();
        season.publish();
        when(episodes.findById(91L)).thenReturn(Optional.of(episode));
        when(mediaAssets.findFirstByEpisode_IdOrderByIdAsc(91L)).thenReturn(Optional.of(media));

        catalog.publishEpisode(91L);
        assertThat(episode.getStatus()).isEqualTo(CatalogStatus.PUBLISHED);
        assertThat(media.getStatus()).isEqualTo(CatalogStatus.PUBLISHED);
        catalog.unpublishEpisode(91L);
        catalog.unpublishEpisode(91L);
        assertThat(episode.getStatus()).isEqualTo(CatalogStatus.DRAFT);
        assertThat(media.getStatus()).isEqualTo(CatalogStatus.DRAFT);
        assertThat(season.getStatus()).isEqualTo(CatalogStatus.PUBLISHED);
        assertThat(movie.getStatus()).isEqualTo(CatalogStatus.PUBLISHED);
        catalog.publishEpisode(91L);
        assertThat(episode.getStatus()).isEqualTo(CatalogStatus.PUBLISHED);
        assertThat(media.getStatus()).isEqualTo(CatalogStatus.PUBLISHED);
    }

    @Test
    void missingVideoPreventsPublication() {
        when(episodes.findById(91L)).thenReturn(Optional.of(episode));
        when(mediaAssets.findFirstByEpisode_IdOrderByIdAsc(91L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalog.publishEpisode(91L)).isInstanceOf(CatalogConflictException.class);
        assertThat(episode.getStatus()).isEqualTo(CatalogStatus.DRAFT);
    }

    @Test
    void staleUnpublishPreservesArchivedPartAndVideo() {
        episode.archive();
        media.archive();
        when(episodes.findById(91L)).thenReturn(Optional.of(episode));

        catalog.unpublishEpisode(91L);

        assertThat(episode.getStatus()).isEqualTo(CatalogStatus.ARCHIVED);
        assertThat(media.getStatus()).isEqualTo(CatalogStatus.ARCHIVED);
    }
}
