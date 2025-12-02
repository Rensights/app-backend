package com.rensights.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FileStorageService {
    
    private static final Logger logger = LoggerFactory.getLogger(FileStorageService.class);
    
    @Value("${app.file-storage.path:./uploads/analysis-requests}")
    private String storagePath;
    
    @Value("${app.file-storage.max-size:10485760}") // 10MB default
    private long maxFileSize;
    
    public List<String> storeFiles(MultipartFile[] files, UUID requestId) throws IOException {
        if (files == null || files.length == 0) {
            return new ArrayList<>();
        }
        
        List<String> filePaths = new ArrayList<>();
        Path requestDir = Paths.get(storagePath, requestId.toString());
        
        // Create directory if it doesn't exist
        if (!Files.exists(requestDir)) {
            Files.createDirectories(requestDir);
        }
        
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }
            
            // Validate file size
            if (file.getSize() > maxFileSize) {
                throw new IllegalArgumentException("File " + file.getOriginalFilename() + " exceeds maximum size of " + (maxFileSize / 1024 / 1024) + "MB");
            }
            
            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String filename = UUID.randomUUID().toString() + extension;
            
            // Store file
            Path targetPath = requestDir.resolve(filename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            
            // Store relative path
            String relativePath = "analysis-requests/" + requestId.toString() + "/" + filename;
            filePaths.add(relativePath);
            
            logger.info("Stored file: {} -> {}", originalFilename, relativePath);
        }
        
        return filePaths;
    }
    
    public byte[] getFile(String filePath) throws IOException {
        Path fullPath = Paths.get(storagePath).resolve(filePath.replace("analysis-requests/", ""));
        if (!Files.exists(fullPath)) {
            throw new IOException("File not found: " + filePath);
        }
        return Files.readAllBytes(fullPath);
    }
    
    public void deleteFiles(List<String> filePaths) {
        for (String filePath : filePaths) {
            try {
                Path fullPath = Paths.get(storagePath).resolve(filePath.replace("analysis-requests/", ""));
                if (Files.exists(fullPath)) {
                    Files.delete(fullPath);
                    logger.info("Deleted file: {}", filePath);
                }
            } catch (IOException e) {
                logger.error("Error deleting file: {}", filePath, e);
            }
        }
    }
}



