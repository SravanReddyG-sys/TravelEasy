package com.traveleasy.bus.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.*;

@Service
@Slf4j
public class BusImageStorageService {

    public static final long MAX_FILE_SIZE_BYTES = 1_572_864L; // 1.5 MB strictly

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );

    private final Path storageDirectory;

    public BusImageStorageService(@Value("${app.upload.dir:uploads/buses}") String uploadDir) {
        this.storageDirectory = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageDirectory);
            log.info("Initialized local bus image storage at: {}", this.storageDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Could not initialize bus image storage directory: " + this.storageDirectory, e);
        }
    }

    public Path getStorageDirectory() {
        return this.storageDirectory;
    }

    /**
     * Validates and stores a multipart image file.
     *
     * @param file the uploaded MultipartFile
     * @param category the category (e.g. "exterior", "interior")
     * @return the generated unique filename
     */
    public String storeImage(MultipartFile file, String category) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot upload an empty image file.");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("Image file size exceeds the strict limit of 1.5 MB (size: " + file.getSize() + " bytes).");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Unsupported image format. Allowed formats are JPG, JPEG, PNG, and WebP.");
        }

        // Validate actual file magic bytes
        try (InputStream in = file.getInputStream()) {
            byte[] header = new byte[12];
            int read = in.read(header);
            if (!isValidImageHeader(header, read)) {
                throw new IllegalArgumentException("Invalid or corrupted image content. Magic byte verification failed.");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read image stream for validation: " + e.getMessage(), e);
        }

        String extension = determineExtension(contentType, file.getOriginalFilename());
        String safeCategory = (category != null && !category.isBlank())
                ? category.toLowerCase().replaceAll("[^a-z0-9_-]", "")
                : "bus";
        String uniqueFilename = safeCategory + "_" + UUID.randomUUID().toString().replace("-", "") + "_" + System.currentTimeMillis() + "." + extension;

        try {
            Path targetLocation = this.storageDirectory.resolve(uniqueFilename).normalize();
            if (!targetLocation.startsWith(this.storageDirectory)) {
                throw new SecurityException("Attempted path traversal attack with filename: " + uniqueFilename);
            }

            try (InputStream in = file.getInputStream()) {
                Files.copy(in, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("Successfully stored bus image: {} (size: {} bytes)", uniqueFilename, file.getSize());
            return uniqueFilename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store image file on local disk: " + e.getMessage(), e);
        }
    }

    /**
     * Loads the image file as a Spring Resource.
     *
     * @param filename the stored filename
     * @return the Resource
     */
    public Resource loadAsResource(String filename) {
        try {
            Path filePath = this.storageDirectory.resolve(filename).normalize();
            if (!filePath.startsWith(this.storageDirectory)) {
                throw new SecurityException("Access to path outside upload directory is forbidden: " + filename);
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new NoSuchElementException("Image file not found or not readable: " + filename);
            }
        } catch (MalformedURLException e) {
            throw new NoSuchElementException("Invalid file URL for image: " + filename);
        }
    }

    /**
     * Deletes an image file from local disk.
     *
     * @param filename the image filename to delete
     * @return true if deleted, false otherwise
     */
    public boolean deleteImage(String filename) {
        if (filename == null || filename.isBlank()) {
            return false;
        }
        try {
            // Strip any leading path components if a URL was passed
            String cleanName = Paths.get(filename).getFileName().toString();
            Path filePath = this.storageDirectory.resolve(cleanName).normalize();
            if (!filePath.startsWith(this.storageDirectory)) {
                log.warn("Security check failed: delete request attempted path outside storage directory: {}", filename);
                return false;
            }
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.info("Deleted orphaned bus image: {}", cleanName);
            }
            return deleted;
        } catch (IOException e) {
            log.error("Failed to delete bus image: {}", filename, e);
            return false;
        }
    }

    /**
     * Validates image magic bytes:
     * - JPEG: FF D8 FF
     * - PNG: 89 50 4E 47 0D 0A 1A 0A
     * - WebP: RIFF (bytes 0-3) + WEBP (bytes 8-11)
     */
    private boolean isValidImageHeader(byte[] header, int length) {
        if (length < 4) {
            return false;
        }

        // JPEG: FF D8 FF
        if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return true;
        }

        // PNG: 89 50 4E 47 (0x89 'P' 'N' 'G')
        if ((header[0] & 0xFF) == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) {
            return true;
        }

        // WebP: RIFF....WEBP
        if (length >= 12) {
            boolean isRiff = header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F';
            boolean isWebp = header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
            if (isRiff && isWebp) {
                return true;
            }
        }

        return false;
    }

    private String determineExtension(String contentType, String originalFilename) {
        if ("image/jpeg".equalsIgnoreCase(contentType) || "image/jpg".equalsIgnoreCase(contentType)) {
            return "jpg";
        }
        if ("image/png".equalsIgnoreCase(contentType)) {
            return "png";
        }
        if ("image/webp".equalsIgnoreCase(contentType)) {
            return "webp";
        }
        if (originalFilename != null && originalFilename.contains(".")) {
            String ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
            if (Set.of("jpg", "jpeg", "png", "webp").contains(ext)) {
                return ext.equals("jpeg") ? "jpg" : ext;
            }
        }
        return "jpg";
    }
}
