package com.londonmeet.server.controller.user;

import com.londonmeet.common.exception.BusinessException;
import com.londonmeet.common.response.ApiResponse;
import com.londonmeet.server.config.GoogleMapsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/map")
@RequiredArgsConstructor
public class MapController {

    private static final String STATIC_MAP_URL = "https://maps.googleapis.com/maps/api/staticmap";

    private final GoogleMapsProperties googleMapsProperties;

    @GetMapping("/static")
    public ApiResponse<Map<?, ?>> staticMap(@RequestParam("address") String address) {
        if (address == null || address.trim().isEmpty()) {
            throw new BusinessException("Address is required.");
        }
        if (!StringUtils.hasText(googleMapsProperties.getApiKey())) {
            return ApiResponse.success(Map.of(
                    "imageUrl", "",
                    "error", "Google Maps API key is not configured."
            ));
        }

        String imageUrl = UriComponentsBuilder.fromUriString(STATIC_MAP_URL)
                .queryParam("center", address.trim())
                .queryParam("zoom", 15)
                .queryParam("size", "800x360")
                .queryParam("scale", 2)
                .queryParam("maptype", "roadmap")
                .queryParam("markers", "color:red|" + address.trim())
                .queryParam("key", googleMapsProperties.getApiKey())
                .toUriString();

        return ApiResponse.success(Map.of(
                "imageUrl", "/api/map/image-proxy?url="
                        + UriUtils.encodeQueryParam(imageUrl, StandardCharsets.UTF_8)
        ));
    }

    @GetMapping("/image-proxy")
    public ResponseEntity<byte[]> imageProxy(@RequestParam("url") String url) {
        URI uri = URI.create(url);
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw new BusinessException("Map image URL is invalid.");
        }
        if (!"maps.googleapis.com".equalsIgnoreCase(uri.getHost())) {
            throw new BusinessException("Map image URL host is invalid.");
        }

        ResponseEntity<byte[]> response;
        try {
            response = RestClient.create()
                    .get()
                    .uri(uri)
                    .retrieve()
                    .toEntity(byte[].class);
        } catch (RestClientException ex) {
            return ResponseEntity.notFound().build();
        }

        MediaType contentType = response.getHeaders().getContentType();
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .contentType(contentType == null ? MediaType.APPLICATION_OCTET_STREAM : contentType)
                .body(response.getBody());
    }
}
