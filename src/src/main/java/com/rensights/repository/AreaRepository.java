package com.rensights.repository;

import com.rensights.model.Area;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AreaRepository extends JpaRepository<Area, UUID> {

    /**
     * The whole dropdown, A-Z — the form shows every area at once.
     *
     * <p>Ordered on LOWER(name) rather than the column itself: Postgres' default collation sorts
     * uppercase ahead of lowercase, which would file "AL Athbah" before "Al Asbaq" and make the
     * list look unsorted to anyone scanning it.
     */
    @Query("SELECT a FROM Area a ORDER BY LOWER(a.name) ASC")
    List<Area> findAllSorted();
}
