package com.rensights.repository;

import com.rensights.model.AnalysisRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AnalysisRequestRepository extends JpaRepository<AnalysisRequest, UUID> {
    Page<AnalysisRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
    
    List<AnalysisRequest> findByEmailOrderByCreatedAtDesc(String email);
    
    List<AnalysisRequest> findByUserIdOrderByCreatedAtDesc(UUID userId);
    
    long countByStatus(AnalysisRequest.AnalysisRequestStatus status);
}

