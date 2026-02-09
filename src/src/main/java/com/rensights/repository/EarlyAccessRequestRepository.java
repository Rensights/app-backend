package com.rensights.repository;

import com.rensights.model.EarlyAccessRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface EarlyAccessRequestRepository extends JpaRepository<EarlyAccessRequest, UUID> {
}
