package com.rensights.repository;

import com.rensights.model.ListedDeal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ListedDealRepository extends JpaRepository<ListedDeal, UUID> {
}

