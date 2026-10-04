package com.acme.moviebot.catalog.internal.persistence;

import com.acme.moviebot.catalog.internal.domain.Episode;
import com.acme.moviebot.catalog.internal.domain.CatalogStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EpisodeRepository extends JpaRepository<Episode, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select episode from Episode episode where episode.id = :id")
    Optional<Episode> findForContentAttachment(@Param("id") long id);

    List<Episode> findBySeason_IdOrderByIdAsc(long seasonId);

    Optional<Episode> findByIdAndStatus(long id, CatalogStatus status);

    long countBySeason_IdAndIdLessThanEqual(long seasonId, long episodeId);
}
