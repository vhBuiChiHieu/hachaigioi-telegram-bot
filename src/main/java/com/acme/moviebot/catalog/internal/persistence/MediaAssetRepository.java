package com.acme.moviebot.catalog.internal.persistence;

import com.acme.moviebot.catalog.internal.domain.MediaAsset;
import com.acme.moviebot.catalog.internal.domain.CatalogStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    Optional<MediaAsset> findFirstByEpisode_IdOrderByIdAsc(long episodeId);

    Optional<MediaAsset> findFirstByEpisode_IdAndStatusOrderByIdAsc(long episodeId, CatalogStatus status);
}
