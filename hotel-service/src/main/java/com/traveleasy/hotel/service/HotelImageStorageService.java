package com.traveleasy.hotel.service;

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
import java.util.stream.Stream;

@Service
@Slf4j
public class HotelImageStorageService {

    public static final long MAX_FILE_SIZE_BYTES = 1_048_576L; // 1.0 MB strictly per requirement

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/pjpeg",
            "image/png",
            "image/webp"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg",
            "jpeg",
            "png",
            "webp"
    );

    private final Path storageDirectory;

    public HotelImageStorageService(@Value("${app.hotel.upload.dir:uploads/hotels}") String uploadDir) {
        this.storageDirectory = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageDirectory);
            log.info("Initialized local hotel image storage at: {}", this.storageDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Could not initialize hotel image storage directory: " + this.storageDirectory, e);
        }
    }

    public Path getStorageDirectory() {
        return this.storageDirectory;
    }

    /**
     * Validates and stores an uploaded hotel image.
     * Supports JPG, JPEG, PNG, and WebP formats.
     * Enforces strict 1.0 MB file size limit and magic-byte verification.
     *
     * @param file the MultipartFile
     * @param category category tag (e.g. "cover", "gallery", or "m1001_cover")
     * @return unique generated filename
     */
    public String storeImage(MultipartFile file, String category) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot upload an empty image file.");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("Image file size exceeds the strict limit of 1 MB (size: " + file.getSize() + " bytes).");
        }

        String contentType = file.getContentType();
        String originalFilename = file.getOriginalFilename();
        String fileExt = extractExtension(originalFilename);

        // Content-Type validation with extension fallback for binary streams
        boolean isContentTypeAllowed = contentType != null && ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase());
        boolean isExtensionAllowed = fileExt != null && ALLOWED_EXTENSIONS.contains(fileExt);

        if (!isContentTypeAllowed && !isExtensionAllowed) {
            throw new IllegalArgumentException("Unsupported image format. Allowed formats are JPG, JPEG, PNG, and WebP.");
        }

        // Validate actual file magic bytes to prevent renamed non-image files
        try (InputStream in = file.getInputStream()) {
            byte[] header = new byte[12];
            int read = in.read(header);
            if (!isValidImageHeader(header, read)) {
                throw new IllegalArgumentException("Invalid or corrupted image content. Magic byte verification failed.");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read image stream for validation: " + e.getMessage(), e);
        }

        String extension = determineExtension(contentType, originalFilename);
        String safeCategory = (category != null && !category.isBlank())
                ? category.toLowerCase().replaceAll("[^a-z0-9_-]", "")
                : "property";
        String uniqueFilename = "hotel_" + safeCategory + "_" + UUID.randomUUID().toString().replace("-", "") + "_" + System.currentTimeMillis() + "." + extension;

        try {
            Path targetLocation = this.storageDirectory.resolve(uniqueFilename).normalize();
            if (!targetLocation.startsWith(this.storageDirectory)) {
                throw new SecurityException("Attempted path traversal attack with filename: " + uniqueFilename);
            }

            try (InputStream in = file.getInputStream()) {
                Files.copy(in, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("Successfully stored hotel image: {} (size: {} bytes)", uniqueFilename, file.getSize());
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
            String cleanName = Paths.get(filename).getFileName().toString();
            Path filePath = this.storageDirectory.resolve(cleanName).normalize();
            if (!filePath.startsWith(this.storageDirectory)) {
                log.warn("Security check failed: delete request attempted path outside storage directory: {}", filename);
                return false;
            }
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.info("Deleted hotel image from disk: {}", cleanName);
            }
            return deleted;
        } catch (IOException e) {
            log.error("Failed to delete hotel image: {}", filename, e);
            return false;
        }
    }

    /**
     * Extracts filename from URL path (e.g. /api/hotels/images/hotel_xxx.jpg -> hotel_xxx.jpg).
     */
    public static String extractFilenameFromUrl(String url) {
        if (url == null || url.isBlank()) return null;
        int lastSlash = url.lastIndexOf('/');
        return (lastSlash >= 0) ? url.substring(lastSlash + 1) : url;
    }

    /**
     * Validates image magic bytes:
     * - JPEG (covers both .jpg and .jpeg): FF D8 FF
     * - PNG: 89 50 4E 47 (0x89 'P' 'N' 'G')
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

        // PNG: 89 50 4E 47
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

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) return null;
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private String determineExtension(String contentType, String originalFilename) {
        String ext = extractExtension(originalFilename);
        if (ext != null && ALLOWED_EXTENSIONS.contains(ext)) {
            return ext.equals("jpeg") ? "jpg" : ext;
        }
        if ("image/jpeg".equalsIgnoreCase(contentType) || "image/jpg".equalsIgnoreCase(contentType) || "image/pjpeg".equalsIgnoreCase(contentType)) {
            return "jpg";
        }
        if ("image/png".equalsIgnoreCase(contentType)) {
            return "png";
        }
        if ("image/webp".equalsIgnoreCase(contentType)) {
            return "webp";
        }
        return "jpg";
    }

    /**
     * Cleanup strategy for abandoned images:
     * Scans storage directory and deletes files older than threshold (e.g. 24h)
     * that are not referenced in active filenames.
     */
    public int cleanupOrphanedFiles(Set<String> activeFilenames, long maxAgeMillis) {
        if (activeFilenames == null) activeFilenames = Collections.emptySet();
        long now = System.currentTimeMillis();
        int deletedCount = 0;

        try (Stream<Path> files = Files.list(this.storageDirectory)) {
            for (Path path : (Iterable<Path>) files::iterator) {
                String fname = path.getFileName().toString();
                if (!activeFilenames.contains(fname)) {
                    try {
                        long lastMod = Files.getLastModifiedTime(path).toMillis();
                        if ((now - lastMod) > maxAgeMillis) {
                            if (Files.deleteIfExists(path)) {
                                deletedCount++;
                                log.info("Cleaned up orphaned hotel image: {}", fname);
                            }
                        }
                    } catch (IOException ignored) {}
                }
            }
        } catch (IOException e) {
            log.error("Failed to run orphaned image cleanup: {}", e.getMessage());
        }
        return deletedCount;
    }
}
