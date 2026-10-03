package com.acme.moviebot.catalog.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.moviebot.catalog.CatalogViews.EpisodeDetails;
import com.acme.moviebot.catalog.CatalogViews.EpisodeMediaView;
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

class CatalogAdminEpisodeQueryTest {

    @Test
    void adminsCanPreviewDraftVideoWhilePublicQueriesStillHideIt() {
        EpisodeRepository episodes = mock(EpisodeRepository.class);
        MediaAssetRepository mediaAssets = mock(MediaAssetRepository.class);
        Episode episode = mock(Episode.class);
        Season season = mock(Season.class);
        Movie movie = new Movie("Phim", null, "phim", null, null, false);
        MediaAsset media = new MediaAsset(episode, "draft-video", null, 7L, 99L, null, "video/mp4", null, null, null, null);
        when(episode.getId()).thenReturn(91L);
        when(episode.getSeason()).thenReturn(season);
        when(episode.getStatus()).thenReturn(CatalogStatus.DRAFT);
        when(season.getId()).thenReturn(9L);
        when(season.getMovie()).thenReturn(movie);
        when(season.getSeasonNumber()).thenReturn(2);
        when(episodes.findById(91L)).thenReturn(Optional.of(episode));
        when(episodes.countBySeason_IdAndIdLessThanEqual(9L, 91L)).thenReturn(3L);
        when(mediaAssets.findFirstByEpisode_IdOrderByIdAsc(91L)).thenReturn(Optional.of(media));
        when(episodes.findByIdAndStatus(91L, CatalogStatus.PUBLISHED)).thenReturn(Optional.empty());
        CatalogQueryService query = new CatalogQueryService(mock(MovieRepository.class),
                mock(SeasonRepository.class), episodes, mediaAssets);

        assertThat(query.findEpisodeForAdmin(91L)).contains(new EpisodeDetails(91L, 9L, 3, "DRAFT"));
        assertThat(query.findEpisodeMediaForAdmin(91L)).contains(new EpisodeMediaView(91L, "Phim", 2, 3, "draft-video"));
        assertThat(query.findEpisodeMedia(91L)).isEmpty();
    }
}
