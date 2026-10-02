package com.acme.moviebot.catalog.internal.persistence;

import com.acme.moviebot.catalog.internal.domain.Movie;
import com.acme.moviebot.catalog.internal.domain.MovieStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovieRepository extends JpaRepository<Movie, Long> {

    boolean existsBySlug(String slug);

    List<Movie> findByStatusOrderByNameAsc(MovieStatus status);

    @Query("select distinct m from Movie m left join m.aliases a " +
            "where m.status = :status and (m.searchName like concat('%', :keyword, '%') " +
            "or a.searchName like concat('%', :keyword, '%')) order by m.name")
    List<Movie> searchPublished(@Param("status") MovieStatus status, @Param("keyword") String keyword, Pageable pageable);

    @Query("select distinct m from Movie m left join m.aliases a " +
            "where m.status <> :archived and (m.searchName like concat('%', :keyword, '%') " +
            "or a.searchName like concat('%', :keyword, '%')) order by m.name")
    List<Movie> searchForAdmin(@Param("archived") MovieStatus archived, @Param("keyword") String keyword, Pageable pageable);
}
