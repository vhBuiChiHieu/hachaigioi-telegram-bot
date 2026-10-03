package com.acme.moviebot.catalog.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.moviebot.catalog.internal.domain.CatalogStatus;
import com.acme.moviebot.catalog.internal.domain.Episode;
import com.acme.moviebot.catalog.internal.domain.Movie;
import com.acme.moviebot.catalog.internal.domain.Season;
import com.acme.moviebot.catalog.internal.persistence.EpisodeRepository;
import com.acme.moviebot.catalog.internal.persistence.MediaAssetRepository;
import com.acme.moviebot.catalog.internal.persistence.MovieRepository;
import com.acme.moviebot.catalog.internal.persistence.SeasonRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CatalogSeasonPublicationTest {

    private final SeasonRepository seasons = mock(SeasonRepository.class);
    private final CatalogManagementService catalog = new CatalogManagementService(mock(MovieRepository.class),
            seasons, mock(EpisodeRepository.class), mock(MediaAssetRepository.class));

    @Test
    void unpublishingReturnsToDraftAndPreservesParentAndParts() {
        Movie movie = new Movie("Phim", "电影", "phim 电影", null, null, false);
        Season season = new Season(movie, 1, 10);
        Episode episode = new Episode(season);
        movie.publish();
        season.publish();
        episode.publish();
        when(seasons.findById(9L)).thenReturn(Optional.of(season));

        catalog.unpublishSeason(9L);
        catalog.unpublishSeason(9L);

        assertThat(season.getStatus()).isEqualTo(CatalogStatus.DRAFT);
        assertThat(movie.getStatus()).isEqualTo(CatalogStatus.PUBLISHED);
        assertThat(episode.getStatus()).isEqualTo(CatalogStatus.PUBLISHED);
        catalog.publishSeason(9L);
        assertThat(season.getStatus()).isEqualTo(CatalogStatus.PUBLISHED);
    }

    @Test
    void staleUnpublishDoesNotRestoreAnArchivedSeason() {
        Season season = new Season(new Movie("Phim", null, "phim", null, null, false), 1, 10);
        season.archive();
        when(seasons.findById(9L)).thenReturn(Optional.of(season));

        catalog.unpublishSeason(9L);

        assertThat(season.getStatus()).isEqualTo(CatalogStatus.ARCHIVED);
    }
}
