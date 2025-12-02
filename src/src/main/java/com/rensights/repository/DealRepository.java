package com.rensights.repository;

import com.rensights.model.Deal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DealRepository extends JpaRepository<Deal, UUID> {
    
    Page<Deal> findByStatusAndCity(Deal.DealStatus status, String city, Pageable pageable);
    
    Page<Deal> findByStatus(Deal.DealStatus status, Pageable pageable);
    
    @Query("SELECT d FROM Deal d WHERE d.status = 'APPROVED' AND d.city = :city " +
           "AND (:area IS NULL OR d.area = :area) " +
           "AND (:bedroomCount IS NULL OR d.bedroomCount = :bedroomCount) " +
           "AND (:buildingStatus IS NULL OR d.buildingStatus = :buildingStatus)")
    Page<Deal> findApprovedDealsWithFilters(@Param("city") String city,
                                             @Param("area") String area,
                                             @Param("bedroomCount") String bedroomCount,
                                             @Param("buildingStatus") Deal.BuildingStatus buildingStatus,
                                             Pageable pageable);
}

