package com.acme.moviebot.catalog.internal.persistence;

import com.acme.moviebot.catalog.internal.domain.MediaAsset;
import com.acme.moviebot.catalog.internal.domain.CatalogStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    // A locking read sees newly committed content even in an existing MySQL repeatable-read transaction.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select asset from MediaAsset asset where asset.episode.id = :episodeId")
    List<MediaAsset> findForContentAttachment(@Param("episodeId") long episodeId);

    Optional<MediaAsset> findFirstByEpisode_IdOrderByIdAsc(long episodeId);

    Optional<MediaAsset> findFirstByEpisode_IdAndStatusOrderByIdAsc(long episodeId, CatalogStatus status);
}
