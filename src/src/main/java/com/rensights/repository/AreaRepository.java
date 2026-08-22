package com.rensights.repository;

import com.rensights.model.Area;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AreaRepository extends JpaRepository<Area, UUID> {

    /** The whole dropdown, alphabetical — the form shows every area at once. */
    List<Area> findAllByOrderByNameAsc();
}
