package com.acme.moviebot.catalog.internal.persistence;

import com.acme.moviebot.catalog.internal.domain.Episode;
import com.acme.moviebot.catalog.internal.domain.EpisodeStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EpisodeRepository extends JpaRepository<Episode, Long> {

    boolean existsBySeason_IdAndEpisodeNumber(long seasonId, int episodeNumber);

    List<Episode> findBySeason_IdAndStatusOrderByEpisodeNumberAsc(long seasonId, EpisodeStatus status);

    Optional<Episode> findByIdAndStatus(long id, EpisodeStatus status);
}
