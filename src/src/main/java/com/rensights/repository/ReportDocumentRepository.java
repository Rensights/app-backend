package com.rensights.repository;

import com.rensights.model.ReportDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReportDocumentRepository extends JpaRepository<ReportDocument, UUID> {
    List<ReportDocument> findBySectionIdAndIsActiveTrueOrderByDisplayOrderAsc(UUID sectionId);
    List<ReportDocument> findBySectionIdOrderByDisplayOrderAsc(UUID sectionId);
}
