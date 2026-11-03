package com.traveleasy.bus.service;

import com.traveleasy.bus.dto.RecipientDto;
import com.traveleasy.bus.dto.RecipientRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class BusRecipientResolutionService {

    private final RestTemplate restTemplate;

    @Value("${auth.service.url:http://localhost:8081}")
    private String authServiceUrl;

    private static final long CACHE_TTL_MILLIS = 10 * 60 * 1000L; // 10 minutes
    private final ConcurrentHashMap<Long, CachedOperator> operatorCache = new ConcurrentHashMap<>();

    public BusRecipientResolutionService(@Autowired(required = false) RestTemplate injectedRestTemplate) {
        if (injectedRestTemplate != null) {
            this.restTemplate = injectedRestTemplate;
            if (injectedRestTemplate.getRequestFactory() instanceof SimpleClientHttpRequestFactory factory) {
                factory.setConnectTimeout(2000);
                factory.setReadTimeout(2000);
            }
        } else {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(2000);
            factory.setReadTimeout(2000);
            this.restTemplate = new RestTemplate(factory);
        }
    }

    private record CachedOperator(String email, String name, long cachedAt) {
        boolean isExpired() {
            return System.currentTimeMillis() - cachedAt > CACHE_TTL_MILLIS;
        }
    }

    /**
     * Resolves the authoritative operator recipient details.
     * Guaranteed fail-safe: will NEVER throw an exception or cause caller transaction rollback.
     */
    public RecipientDto resolveOperatorRecipient(Long operatorId, Long callerUserId, String callerEmail, boolean callerIsAdmin) {
        if (operatorId == null || operatorId <= 0) {
            log.warn("Cannot resolve operator recipient for missing or invalid operatorId: {}", operatorId);
            return null;
        }

        try {
            // Case 1: Caller is the operator acting on their own resource (never when caller is admin)
            if (!callerIsAdmin && callerUserId != null && callerUserId.equals(operatorId) && callerEmail != null && !callerEmail.isBlank()) {
                String operatorName = resolveOperatorNameFromCacheOrAuth(operatorId, callerEmail);
                List<String> channels = new ArrayList<>();
                channels.add("IN_APP");
                channels.add("EMAIL");

                return RecipientDto.builder()
                        .role(RecipientRole.BUS_OPERATOR)
                        .userId(operatorId)
                        .email(callerEmail.trim())
                        .name(operatorName)
                        .channels(channels)
                        .build();
            }

            // Case 2: Caller is Admin, internal service, or background task -> Authoritative Auth Service lookup
            CachedOperator cached = operatorCache.get(operatorId);
            if (cached != null && !cached.isExpired()) {
                return buildRecipientFromCache(operatorId, cached);
            }

            String url = authServiceUrl.replaceAll("/+$", "") + "/api/users/" + operatorId;
            @SuppressWarnings("unchecked")
            Map<String, Object> userMap = restTemplate.getForObject(url, Map.class);

            if (userMap != null) {
                String email = (String) userMap.get("email");
                String legalOrg = (String) userMap.get("legalOrganizationName");
                String fullName = (String) userMap.get("fullName");
                String name = (legalOrg != null && !legalOrg.isBlank()) ? legalOrg.trim()
                        : ((fullName != null && !fullName.isBlank()) ? fullName.trim() : email);

                if (email != null && !email.isBlank()) {
                    email = email.trim();
                    operatorCache.put(operatorId, new CachedOperator(email, name, System.currentTimeMillis()));

                    List<String> channels = new ArrayList<>();
                    channels.add("IN_APP");
                    channels.add("EMAIL");

                    return RecipientDto.builder()
                            .role(RecipientRole.BUS_OPERATOR)
                            .userId(operatorId)
                            .email(email)
                            .name(name)
                            .channels(channels)
                            .build();
                }
            }
        } catch (Exception ex) {
            log.warn("Auth Service operator lookup failed for operatorId {}: {}. Falling back to IN_APP channel.",
                    operatorId, ex.getMessage());
        }

        // Safe fallback: IN_APP only, no email, no transaction failure
        return RecipientDto.builder()
                .role(RecipientRole.BUS_OPERATOR)
                .userId(operatorId)
                .channels(List.of("IN_APP"))
                .build();
    }

    private String resolveOperatorNameFromCacheOrAuth(Long operatorId, String fallback) {
        CachedOperator cached = operatorCache.get(operatorId);
        if (cached != null && !cached.isExpired() && cached.name() != null) {
            return cached.name();
        }
        try {
            String url = authServiceUrl.replaceAll("/+$", "") + "/api/users/" + operatorId;
            @SuppressWarnings("unchecked")
            Map<String, Object> userMap = restTemplate.getForObject(url, Map.class);
            if (userMap != null) {
                String legalOrg = (String) userMap.get("legalOrganizationName");
                String fullName = (String) userMap.get("fullName");
                String name = (legalOrg != null && !legalOrg.isBlank()) ? legalOrg.trim()
                        : ((fullName != null && !fullName.isBlank()) ? fullName.trim() : fallback);
                operatorCache.put(operatorId, new CachedOperator(fallback, name, System.currentTimeMillis()));
                return name;
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private RecipientDto buildRecipientFromCache(Long operatorId, CachedOperator cached) {
        List<String> channels = new ArrayList<>();
        channels.add("IN_APP");
        if (cached.email() != null && !cached.email().isBlank()) {
            channels.add("EMAIL");
        }
        return RecipientDto.builder()
                .role(RecipientRole.BUS_OPERATOR)
                .userId(operatorId)
                .email(cached.email())
                .name(cached.name())
                .channels(channels)
                .build();
    }
}
