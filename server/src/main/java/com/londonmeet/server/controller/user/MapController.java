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
    private static final String GEOCODE_URL = "https://maps.googleapis.com/maps/api/geocode/json";

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

        Map<?, ?> location = resolveAddressLocation(address.trim());
        if (location == null) {
            return ApiResponse.success(Map.of(
                    "imageUrl", "",
                    "error", "Address is unavailable."
            ));
        }
        Object lat = location.get("lat");
        Object lng = location.get("lng");
        String center = lat + "," + lng;

        String imageUrl = UriComponentsBuilder.fromUriString(STATIC_MAP_URL)
                .queryParam("center", center)
                .queryParam("zoom", 15)
                .queryParam("size", "800x360")
                .queryParam("scale", 2)
                .queryParam("maptype", "roadmap")
                .queryParam("markers", "color:red|" + center)
                .queryParam("key", googleMapsProperties.getApiKey())
                .toUriString();

        return ApiResponse.success(Map.of(
                "imageUrl", "/api/map/image-proxy?url="
                        + UriUtils.encodeQueryParam(imageUrl, StandardCharsets.UTF_8)
        ));
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> resolveAddressLocation(String address) {
        String geocodeUrl = UriComponentsBuilder.fromUriString(GEOCODE_URL)
                .queryParam("address", address)
                .queryParam("key", googleMapsProperties.getApiKey())
                .toUriString();
        try {
            Map<String, Object> response = RestClient.create()
                    .get()
                    .uri(URI.create(geocodeUrl))
                    .retrieve()
                    .body(Map.class);
            if (response == null || !"OK".equals(response.get("status"))) {
                return null;
            }
            Object resultsValue = response.get("results");
            if (!(resultsValue instanceof java.util.List<?> results) || results.isEmpty()) {
                return null;
            }
            Object firstValue = results.get(0);
            if (!(firstValue instanceof Map<?, ?> first)) {
                return null;
            }
            Object geometryValue = first.get("geometry");
            if (!(geometryValue instanceof Map<?, ?> geometry)) {
                return null;
            }
            Object locationValue = geometry.get("location");
            if (!(locationValue instanceof Map<?, ?> location)) {
                return null;
            }
            return location;
        } catch (RuntimeException ex) {
            return null;
        }
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
