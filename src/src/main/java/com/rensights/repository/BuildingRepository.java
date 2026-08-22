package com.rensights.repository;

import com.rensights.model.Building;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BuildingRepository extends JpaRepository<Building, UUID> {

    /**
     * Type-ahead lookup.
     *
     * <p>Matches anywhere in the name so "pinnacle" finds "Marina Pinnacle Tower", but orders
     * names that <em>start</em> with what was typed first — those are almost always what the
     * user meant. Case-insensitive on both sides.
     *
     * @param query already lowercased by the caller
     */
    @Query("SELECT b FROM Building b WHERE LOWER(b.name) LIKE CONCAT('%', :query, '%') "
        + "ORDER BY CASE WHEN LOWER(b.name) LIKE CONCAT(:query, '%') THEN 0 ELSE 1 END, b.name ASC")
    List<Building> search(@Param("query") String query, Pageable pageable);
}
