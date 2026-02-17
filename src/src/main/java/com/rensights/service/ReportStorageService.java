package com.rensights.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class ReportStorageService {

    @Value("${reports.storage.path:/data/reports}")
    private String storagePath;

    public Resource loadAsResource(String relativePath) {
        Path path = Paths.get(storagePath).resolve(relativePath).normalize();
        return new FileSystemResource(path);
    }
}
