package com.rensights.repository;

import com.rensights.model.Language;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LanguageRepository extends JpaRepository<Language, UUID> {
    
    Optional<Language> findByCode(String code);
    
    List<Language> findByEnabledTrueOrderByNameAsc();
}


