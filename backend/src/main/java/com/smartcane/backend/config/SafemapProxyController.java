package com.smartcane.backend.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * safemap(생활안전지도) WMS 프록시
 *
 * 확인된 설정:
 *  - URL    : https://www.safemap.go.kr/openapi2/IF_0097_WMS  (https + www + 소문자)
 *  - 인증   : serviceKey
 *  - 좌표계 : srs=EPSG:3857 (OpenLayers 기본값과 동일, 변환 불필요)
 *
 * 성능:
 *  - HTTP 커넥션 풀 (최대 50, 호스트당 20)
 *  - 타일 인메모리 캐시 (5분 TTL)
 */
@Tag(name = "safemap-proxy-controller", description = "행안부 생활안전지도 WMS 타일 프록시. API 키를 서버에서 관리하며 앱의 CORS 문제를 해결하고 5분 인메모리 캐시로 중복 요청을 줄입니다.")
@RestController
@RequestMapping("/api/proxy/safemap")
public class SafemapProxyController {

    private static final Logger log = LoggerFactory.getLogger(SafemapProxyController.class);

    private static final String BASE = "https://www.safemap.go.kr/openapi2/";
    private static final Map<String, String> LAYER_URL = Map.of(
            "A2SM_RBLNG_1", BASE + "IF_0095_WMS",
            "A2SM_RBLNG_3", BASE + "IF_0097_WMS"
    );

    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    @Value("${app.safemap.api-key:}")
    private String safemapApiKey;

    private final RestTemplate restTemplate;

    private final ConcurrentHashMap<String, byte[]> tileCache   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long>   tileCacheTs = new ConcurrentHashMap<>();

    public SafemapProxyController() {
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(50);
        cm.setDefaultMaxPerRoute(20);

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(cm)
                .build();

        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(httpClient);
        factory.setConnectTimeout(3000);
        factory.setConnectionRequestTimeout(3000);

        this.restTemplate = new RestTemplate(factory);
    }

    @Operation(summary = "WMS 타일 프록시", description = "앱으로부터 받은 bbox·width·height 파라미터를 그대로 safemap WMS에 전달하고 PNG 타일을 반환합니다. 동일 요청은 5분간 캐시됩니다(X-Cache: HIT/MISS).")
    @GetMapping
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) {

        String layerParam = request.getParameter("LAYERS");
        if (layerParam == null) layerParam = request.getParameter("layers");
        if (layerParam == null) layerParam = request.getParameter("layer");
        String wmsUrl = (layerParam != null && LAYER_URL.containsKey(layerParam))
                ? LAYER_URL.get(layerParam)
                : BASE + "IF_0097_WMS";

        String bbox   = firstNonNull(request.getParameter("bbox"),   request.getParameter("BBOX"));
        String width  = firstNonNull(request.getParameter("width"),  request.getParameter("WIDTH"));
        String height = firstNonNull(request.getParameter("height"), request.getParameter("HEIGHT"));

        String cacheKey = wmsUrl + "|" + bbox + "|" + width + "|" + height;

        byte[] cached = tileCache.get(cacheKey);
        Long ts = tileCacheTs.get(cacheKey);
        if (cached != null && ts != null && (System.currentTimeMillis() - ts) < CACHE_TTL_MS) {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.IMAGE_PNG);
            h.set("X-Cache", "HIT");
            return new ResponseEntity<>(cached, h, HttpStatus.OK);
        }

        StringBuilder sb = new StringBuilder(wmsUrl)
                .append("?serviceKey=").append(safemapApiKey)
                .append("&srs=EPSG:3857")
                .append("&format=image/png")
                .append("&transparent=TRUE");
        if (bbox   != null) sb.append("&bbox=").append(bbox);
        if (width  != null) sb.append("&width=").append(width);
        if (height != null) sb.append("&height=").append(height);

        String uri = sb.toString();
        log.debug("safemap 프록시 → {}", uri);

        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    uri, HttpMethod.GET, null, byte[].class);

            MediaType contentType = response.getHeaders().getContentType();

            boolean isErrorPage = contentType == null
                    || contentType.includes(MediaType.TEXT_HTML)
                    || contentType.includes(MediaType.TEXT_XML);

            if (isErrorPage) {
                log.warn("safemap 오류 응답 ContentType={}", contentType);
                return ResponseEntity.noContent().build();
            }

            byte[] body = response.getBody();
            if (body != null) {
                tileCache.put(cacheKey, body);
                tileCacheTs.put(cacheKey, System.currentTimeMillis());
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.set("X-Cache", "MISS");
            return new ResponseEntity<>(body, headers, HttpStatus.OK);

        } catch (Exception e) {
            log.error("safemap 프록시 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    private String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
