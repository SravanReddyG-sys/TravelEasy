package com.traveleasy.gateway.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

@RestController
public class GatewayController {

    @Value("${auth.service.url:http://localhost:8081}")
    private String authServiceUrl;

    @Value("${bus.service.url:http://localhost:8082}")
    private String busServiceUrl;

    @Value("${hotel.service.url:http://localhost:8083}")
    private String hotelServiceUrl;

    @Value("${booking.service.url:http://localhost:8084}")
    private String bookingServiceUrl;

    @Value("${payment.service.url:http://localhost:8085}")
    private String paymentServiceUrl;

    @Value("${notification.service.url:http://localhost:8086}")
    private String notificationServiceUrl;

    @Value("${review.service.url:http://localhost:8087}")
    private String reviewServiceUrl;

    @Value("${traveller.service.url:http://localhost:8088}")
    private String travellerServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate(new org.springframework.http.client.JdkClientHttpRequestFactory());

    @RequestMapping("/api/**")
    public ResponseEntity<byte[]> proxyRequest(@RequestBody(required = false) byte[] body,
                                               HttpMethod method,
                                               HttpServletRequest request) {
        String path = request.getRequestURI();
        String query = request.getQueryString();

        String targetBaseUrl;
        if (path.startsWith("/api/auth") || path.startsWith("/api/users")) {
            targetBaseUrl = authServiceUrl;
        } else if (path.startsWith("/api/buses")) {
            targetBaseUrl = busServiceUrl;
        } else if (path.startsWith("/api/hotels")) {
            targetBaseUrl = hotelServiceUrl;
        } else if (path.startsWith("/api/bookings")) {
            targetBaseUrl = bookingServiceUrl;
        } else if (path.startsWith("/api/payments")) {
            targetBaseUrl = paymentServiceUrl;
        } else if (path.startsWith("/api/notifications") || path.startsWith("/api/newsletter")) {
            targetBaseUrl = notificationServiceUrl;
        } else if (path.startsWith("/api/reviews")) {
            targetBaseUrl = reviewServiceUrl;
        } else if (path.startsWith("/api/traveller")) {
            targetBaseUrl = travellerServiceUrl;
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Route not found in API Gateway".getBytes(StandardCharsets.UTF_8));
        }

        String targetUrl = targetBaseUrl + path + (query != null ? "?" + query : "");

        try {
            // Check if this is a multipart request parsed by Spring MVC
            if (request instanceof MultipartHttpServletRequest multipartRequest) {
                MultiValueMap<String, Object> multipartBody = new LinkedMultiValueMap<>();
                multipartRequest.getFileMap().forEach((partName, file) -> {
                    try {
                        final String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.bin";
                        ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                            @Override
                            public String getFilename() {
                                return filename;
                            }
                        };
                        multipartBody.add(partName, fileResource);
                    } catch (Exception ex) {
                        multipartBody.add(partName, file.getResource());
                    }
                });
                multipartRequest.getParameterMap().forEach((paramName, values) -> {
                    for (String val : values) {
                        multipartBody.add(paramName, val);
                    }
                });

                HttpHeaders headers = new HttpHeaders();
                Enumeration<String> headerNames = request.getHeaderNames();
                while (headerNames.hasMoreElements()) {
                    String name = headerNames.nextElement();
                    if (!name.equalsIgnoreCase("host") && !name.equalsIgnoreCase("content-length") && !name.equalsIgnoreCase("content-type") && !name.equalsIgnoreCase("x-internal-service") && !name.toLowerCase().startsWith("x-internal-")) {
                        headers.add(name, request.getHeader(name));
                    }
                }
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);

                HttpEntity<MultiValueMap<String, Object>> multipartEntity = new HttpEntity<>(multipartBody, headers);
                ResponseEntity<byte[]> response = restTemplate.exchange(URI.create(targetUrl), method, multipartEntity, byte[].class);
                return copyResponse(response);
            }

            // Normal JSON / binary / raw request
            HttpHeaders headers = new HttpHeaders();
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                if (!name.equalsIgnoreCase("host") && !name.equalsIgnoreCase("content-length") && !name.equalsIgnoreCase("x-internal-service") && !name.toLowerCase().startsWith("x-internal-")) {
                    headers.add(name, request.getHeader(name));
                }
            }

            HttpEntity<byte[]> entity = new HttpEntity<>(body, headers);
            ResponseEntity<byte[]> response = restTemplate.exchange(URI.create(targetUrl), method, entity, byte[].class);
            return copyResponse(response);

        } catch (HttpStatusCodeException e) {
            HttpHeaders responseHeaders = new HttpHeaders();
            if (e.getResponseHeaders() != null) {
                e.getResponseHeaders().forEach((name, values) -> {
                    if (!name.equalsIgnoreCase("transfer-encoding") && !name.equalsIgnoreCase("content-length")) {
                        responseHeaders.put(name, values);
                    }
                });
            }
            return new ResponseEntity<>(e.getResponseBodyAsByteArray(), responseHeaders, e.getStatusCode());
        } catch (ResourceAccessException e) {
            HttpHeaders errHeaders = new HttpHeaders();
            errHeaders.setContentType(MediaType.APPLICATION_JSON);
            String safeMsg = e.getMessage() != null ? e.getMessage().replace("\"", "'") : "Connection refused";
            String jsonError = "{\"error\":\"Bad Gateway\",\"message\":\"Downstream service unavailable: " + safeMsg + "\",\"status\":502}";
            return new ResponseEntity<>(jsonError.getBytes(StandardCharsets.UTF_8), errHeaders, HttpStatus.BAD_GATEWAY);
        } catch (Exception e) {
            HttpHeaders errHeaders = new HttpHeaders();
            errHeaders.setContentType(MediaType.APPLICATION_JSON);
            String safeMsg = e.getMessage() != null ? e.getMessage().replace("\"", "'") : "Internal error";
            String jsonError = "{\"error\":\"Internal Server Error\",\"message\":\"Gateway Error: " + safeMsg + "\",\"status\":500}";
            return new ResponseEntity<>(jsonError.getBytes(StandardCharsets.UTF_8), errHeaders, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private ResponseEntity<byte[]> copyResponse(ResponseEntity<byte[]> response) {
        HttpHeaders responseHeaders = new HttpHeaders();
        if (response.getHeaders() != null) {
            response.getHeaders().forEach((name, values) -> {
                if (!name.equalsIgnoreCase("transfer-encoding") && !name.equalsIgnoreCase("content-length")) {
                    responseHeaders.put(name, values);
                }
            });
        }
        return new ResponseEntity<>(response.getBody(), responseHeaders, response.getStatusCode());
    }
}
