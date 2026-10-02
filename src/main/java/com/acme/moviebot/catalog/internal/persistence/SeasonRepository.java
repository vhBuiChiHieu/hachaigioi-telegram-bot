package com.acme.moviebot.catalog.internal.persistence;

import com.acme.moviebot.catalog.internal.domain.Season;
import com.acme.moviebot.catalog.internal.domain.CatalogStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeasonRepository extends JpaRepository<Season, Long> {

    boolean existsByMovie_IdAndSeasonNumber(long movieId, int seasonNumber);

    List<Season> findByMovie_IdAndStatusOrderBySeasonNumberAsc(long movieId, CatalogStatus status);

    List<Season> findByMovie_IdOrderBySeasonNumberAsc(long movieId);
}
