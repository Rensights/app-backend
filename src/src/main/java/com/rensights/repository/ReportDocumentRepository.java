package com.rensights.repository;

import com.rensights.model.ReportDocument;
import com.rensights.model.UserTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportDocumentRepository extends JpaRepository<ReportDocument, UUID> {
    List<ReportDocument> findBySectionIdAndIsActiveTrueOrderByDisplayOrderAsc(UUID sectionId);
    List<ReportDocument> findBySectionIdOrderByDisplayOrderAsc(UUID sectionId);

    // Access tier of a document's owning section, without loading the lazy relation.
    @Query("select d.section.accessTier from ReportDocument d where d.id = :id")
    Optional<UserTier> findAccessTierByDocumentId(@Param("id") UUID id);
}
