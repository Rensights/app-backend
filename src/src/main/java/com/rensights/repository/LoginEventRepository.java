package com.rensights.repository;

import com.rensights.model.LoginEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LoginEventRepository extends JpaRepository<LoginEvent, UUID> {
}
