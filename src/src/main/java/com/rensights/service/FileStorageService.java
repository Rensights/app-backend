package com.rensights.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {
    
    private static final Logger logger = LoggerFactory.getLogger(FileStorageService.class);
    
    @Value("${app.file-storage.path:./uploads/analysis-requests}")
    private String storagePath;
    
    @Value("${app.file-storage.max-size:10485760}") // 10MB default
    private long maxFileSize;
    
    // SECURITY: Allowed MIME types for file uploads
    private static final Set<String> ALLOWED_MIME_TYPES = new HashSet<>(Arrays.asList(
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/gif",
        "image/webp",
        "application/pdf"
    ));
    
    // SECURITY: Allowed file extensions (secondary validation)
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
        ".jpg", ".jpeg", ".png", ".gif", ".webp", ".pdf"
    ));
    
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
            
            String originalFilename = file.getOriginalFilename();
            
            // SECURITY: Validate MIME type
            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
                logger.warn("SECURITY ALERT: Rejected file upload with invalid MIME type. File: {}, MIME: {}", 
                    originalFilename, contentType);
                throw new IllegalArgumentException("Invalid file type. Allowed types: images (JPEG, PNG, GIF, WebP) and PDF");
            }
            
            // SECURITY: Validate file extension (secondary check)
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
                if (!ALLOWED_EXTENSIONS.contains(extension)) {
                    logger.warn("SECURITY ALERT: Rejected file upload with invalid extension. File: {}, Extension: {}", 
                        originalFilename, extension);
                    throw new IllegalArgumentException("Invalid file extension. Allowed extensions: .jpg, .jpeg, .png, .gif, .webp, .pdf");
                }
            }
            
            // SECURITY: Validate file content (magic bytes) - basic check
            try (InputStream inputStream = file.getInputStream()) {
                byte[] header = new byte[4];
                int bytesRead = inputStream.read(header);
                if (bytesRead >= 4) {
                    // Check magic bytes for common image formats
                    boolean isValidFile = false;
                    if (contentType.startsWith("image/")) {
                        // JPEG: FF D8 FF
                        if (header[0] == (byte)0xFF && header[1] == (byte)0xD8 && header[2] == (byte)0xFF) {
                            isValidFile = true;
                        }
                        // PNG: 89 50 4E 47
                        else if (header[0] == (byte)0x89 && header[1] == (byte)0x50 && 
                                 header[2] == (byte)0x4E && header[3] == (byte)0x47) {
                            isValidFile = true;
                        }
                        // GIF: 47 49 46 38
                        else if (header[0] == (byte)0x47 && header[1] == (byte)0x49 && 
                                 header[2] == (byte)0x46 && header[3] == (byte)0x38) {
                            isValidFile = true;
                        }
                        // WebP: RIFF header (simplified check)
                        else if (header[0] == (byte)0x52 && header[1] == (byte)0x49) {
                            isValidFile = true;
                        }
                    } else if (contentType.equals("application/pdf")) {
                        // PDF: %PDF
                        if (header[0] == (byte)0x25 && header[1] == (byte)0x50 && 
                            header[2] == (byte)0x44 && header[3] == (byte)0x46) {
                            isValidFile = true;
                        }
                    }
                    
                    if (!isValidFile && bytesRead >= 4) {
                        logger.warn("SECURITY ALERT: File content validation failed. File: {}, ContentType: {}", 
                            originalFilename, contentType);
                        throw new IllegalArgumentException("File content does not match declared file type");
                    }
                }
            }
            
            // Generate unique filename
            String filename = UUID.randomUUID().toString() + extension;
            
            // Store file
            Path targetPath = requestDir.resolve(filename);
            
            // Reset input stream for actual copy
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            
            // SECURITY: Set file permissions (read-only for owner, no execute)
            try {
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-r--r--");
                Files.setPosixFilePermissions(targetPath, perms);
            } catch (UnsupportedOperationException e) {
                // Windows or filesystem doesn't support POSIX permissions - log and continue
                logger.debug("Could not set POSIX permissions (may be Windows): {}", e.getMessage());
            }
            
            // Store relative path
            String relativePath = "analysis-requests/" + requestId.toString() + "/" + filename;
            filePaths.add(relativePath);
            
            logger.info("Stored file: {} -> {} (validated MIME: {})", originalFilename, relativePath, contentType);
        }
        
        return filePaths;
    }
    
    public byte[] getFile(String filePath) throws IOException {
        // CRITICAL SECURITY FIX: Prevent path traversal attacks
        // Normalize base directory path
        Path baseDir = Paths.get(storagePath).normalize().toAbsolutePath();
        
        // Remove "analysis-requests/" prefix if present and normalize
        String cleanedPath = filePath.replace("analysis-requests/", "");
        // Remove any leading slashes or dots that could indicate path traversal
        cleanedPath = cleanedPath.replaceAll("^[/\\.]+", "");
        
        // Resolve and normalize the full path
        Path resolvedPath = baseDir.resolve(cleanedPath).normalize().toAbsolutePath();
        
        // CRITICAL: Ensure resolved path is within base directory (prevents path traversal)
        if (!resolvedPath.startsWith(baseDir)) {
            logger.error("SECURITY ALERT: Path traversal attempt detected. Base: {}, Requested: {}, Resolved: {}", 
                baseDir, filePath, resolvedPath);
            throw new SecurityException("Invalid file path: path traversal detected");
        }
        
        if (!Files.exists(resolvedPath)) {
            throw new IOException("File not found: " + filePath);
        }
        
        // Additional security: Ensure it's a regular file (not a directory or symlink)
        if (!Files.isRegularFile(resolvedPath)) {
            throw new SecurityException("Invalid file path: not a regular file");
        }
        
        return Files.readAllBytes(resolvedPath);
    }
    
    public void deleteFiles(List<String> filePaths) {
        // CRITICAL SECURITY FIX: Prevent path traversal in deletion
        Path baseDir = Paths.get(storagePath).normalize().toAbsolutePath();
        
        for (String filePath : filePaths) {
            try {
                // Clean and validate path
                String cleanedPath = filePath.replace("analysis-requests/", "");
                cleanedPath = cleanedPath.replaceAll("^[/\\.]+", "");
                
                Path resolvedPath = baseDir.resolve(cleanedPath).normalize().toAbsolutePath();
                
                // Validate path is within base directory
                if (!resolvedPath.startsWith(baseDir)) {
                    logger.error("SECURITY ALERT: Path traversal attempt in delete. Base: {}, Requested: {}", 
                        baseDir, filePath);
                    continue; // Skip this file rather than throw to avoid exposing internal structure
                }
                
                if (Files.exists(resolvedPath) && Files.isRegularFile(resolvedPath)) {
                    Files.delete(resolvedPath);
                    logger.info("Deleted file: {}", filePath);
                }
            } catch (IOException e) {
                logger.error("Error deleting file: {}", filePath, e);
            } catch (SecurityException e) {
                logger.error("Security error deleting file: {}", filePath, e);
            }
        }
    }
}



