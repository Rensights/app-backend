package com.rensights.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Reads article images from the shared reports volume that admin-backend
 * writes to via its own ArticleImageStorageService (same underlying PVC,
 * same "reports.storage.path" config, same pattern as ReportStorageService).
 */
@Service
public class ArticleImageStorageService {

    @Value("${reports.storage.path:/data/reports}")
    private String storagePath;

    public Resource loadAsResource(String filename) {
        Path path = Paths.get(storagePath, "article-images").resolve(filename).normalize();
        return new FileSystemResource(path);
    }
}
