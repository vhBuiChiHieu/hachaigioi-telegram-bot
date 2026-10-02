package com.acme.moviebot.catalog.internal.persistence;

import com.acme.moviebot.catalog.internal.domain.Episode;
import com.acme.moviebot.catalog.internal.domain.CatalogStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EpisodeRepository extends JpaRepository<Episode, Long> {

    List<Episode> findBySeason_IdOrderByIdAsc(long seasonId);

    Optional<Episode> findByIdAndStatus(long id, CatalogStatus status);

    long countBySeason_IdAndIdLessThanEqual(long seasonId, long episodeId);
}
