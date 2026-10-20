package com.traveleasy.auth.service;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides Base64-encoded PNG assets for inline email attachments.
 * Assets are read directly from the classpath (/email-assets/) and cached in memory.
 */
@Slf4j
public class EmailAssetProvider {

    public static final String LOGO_CONTENT_ID = "travel-easy-logo";
    public static final String LOCK_CONTENT_ID = "lock-icon";
    public static final String SHIELD_CONTENT_ID = "shield-icon";

    private static final String LOGO_RESOURCE = "/email-assets/travel-easy-logo.png";
    private static final String LOCK_RESOURCE = "/email-assets/lock-icon.png";
    private static final String SHIELD_RESOURCE = "/email-assets/shield-icon.png";

    private static String cachedLogoBase64;
    private static String cachedLockBase64;
    private static String cachedShieldBase64;

    public static synchronized String getTravelEasyLogoBase64() {
        if (cachedLogoBase64 == null) {
            cachedLogoBase64 = loadResourceAsBase64(LOGO_RESOURCE);
        }
        return cachedLogoBase64;
    }

    public static synchronized String getLockIconBase64() {
        if (cachedLockBase64 == null) {
            cachedLockBase64 = loadResourceAsBase64(LOCK_RESOURCE);
        }
        return cachedLockBase64;
    }

    public static synchronized String getShieldIconBase64() {
        if (cachedShieldBase64 == null) {
            cachedShieldBase64 = loadResourceAsBase64(SHIELD_RESOURCE);
        }
        return cachedShieldBase64;
    }

    /**
     * Builds the attachment list required by the Resend email API to support
     * inline CID image references in email templates.
     */
    public static List<Map<String, String>> getEmailAttachments() {
        List<Map<String, String>> attachments = new ArrayList<>(3);

        String logo = getTravelEasyLogoBase64();
        if (!logo.isEmpty()) {
            Map<String, String> item = new HashMap<>();
            item.put("filename", "travel-easy-logo.png");
            item.put("content", logo);
            item.put("content_id", LOGO_CONTENT_ID);
            attachments.add(item);
        }

        String lock = getLockIconBase64();
        if (!lock.isEmpty()) {
            Map<String, String> item = new HashMap<>();
            item.put("filename", "lock-icon.png");
            item.put("content", lock);
            item.put("content_id", LOCK_CONTENT_ID);
            attachments.add(item);
        }

        String shield = getShieldIconBase64();
        if (!shield.isEmpty()) {
            Map<String, String> item = new HashMap<>();
            item.put("filename", "shield-icon.png");
            item.put("content", shield);
            item.put("content_id", SHIELD_CONTENT_ID);
            attachments.add(item);
        }

        return Collections.unmodifiableList(attachments);
    }

    private static String loadResourceAsBase64(String resourcePath) {
        try (InputStream in = EmailAssetProvider.class.getResourceAsStream(resourcePath)) {
            if (in != null) {
                byte[] bytes = in.readAllBytes();
                return Base64.getEncoder().encodeToString(bytes);
            } else {
                log.warn("Email asset resource not found on classpath: {}", resourcePath);
                return "";
            }
        } catch (IOException e) {
            log.error("Failed reading classpath asset {}: {}", resourcePath, e.getMessage(), e);
            return "";
        }
    }
}
