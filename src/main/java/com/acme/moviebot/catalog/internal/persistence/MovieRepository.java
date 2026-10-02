package com.acme.moviebot.catalog.internal.persistence;

import com.acme.moviebot.catalog.internal.domain.Movie;
import com.acme.moviebot.catalog.internal.domain.CatalogStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieRepository extends JpaRepository<Movie, Long> {

    List<Movie> findByStatusOrderByVietnameseNameAsc(CatalogStatus status);

    List<Movie> findByStatusNotOrderByVietnameseNameAsc(CatalogStatus excludedStatus);
}
