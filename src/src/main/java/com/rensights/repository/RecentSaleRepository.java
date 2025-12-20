package com.rensights.repository;

import com.rensights.model.RecentSale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RecentSaleRepository extends JpaRepository<RecentSale, UUID> {
}

