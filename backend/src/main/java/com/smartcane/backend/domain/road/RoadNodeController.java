package com.smartcane.backend.domain.road;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ITS 도로 노드(교차로) 데이터 API.
 * its_gj_nodes 테이블에서 bbox 내 노드를 조회하여 GeoJSON으로 반환.
 * PostgreSQL 설정이 없는 환경(CI/테스트)에서는 빈 생성 제외.
 */
@ConditionalOnExpression("'${app.datasource.postgres.host:}' != ''")
@RestController
@RequestMapping("/road")
public class RoadNodeController {

    private final JdbcTemplate jdbc;

    public RoadNodeController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * bbox 내 ITS 노드(교차로) 조회.
     * @param minLng 최소 경도
     * @param minLat 최소 위도
     * @param maxLng 최대 경도
     * @param maxLat 최대 위도
     * @param nodeType 노드 유형 필터 (선택)
     * @param limit   최대 반환 수 (기본 1000)
     */
    @GetMapping("/nodes")
    public Map<String, Object> getNodes(
            @RequestParam double minLng,
            @RequestParam double minLat,
            @RequestParam double maxLng,
            @RequestParam double maxLat,
            @RequestParam(required = false) String nodeType,
            @RequestParam(defaultValue = "1000") int limit
    ) {
        // bbox(4326) → 5179 역변환 후 원본 좌표계로 비교 → 공간인덱스 활용 가능
        // 좌표 출력만 4326으로 변환
        String sql = """
                SELECT
                    ogc_fid,
                    node_id,
                    node_type,
                    node_name,
                    turn_p,
                    ST_X(ST_Transform(geom, 4326)) AS lng,
                    ST_Y(ST_Transform(geom, 4326)) AS lat
                FROM its_gj_nodes
                WHERE ST_Within(
                    geom,
                    ST_Transform(ST_MakeEnvelope(?, ?, ?, ?, 4326), 5179)
                )
                """ +
                (nodeType != null && !nodeType.isBlank() ? " AND node_type = ?" : "") +
                " LIMIT ?";

        Object[] params = nodeType != null && !nodeType.isBlank()
                ? new Object[]{minLng, minLat, maxLng, maxLat, nodeType, limit}
                : new Object[]{minLng, minLat, maxLng, maxLat, limit};

        List<Map<String, Object>> rows = jdbc.queryForList(sql, params);

        List<Map<String, Object>> features = rows.stream().map(row -> {
            Map<String, Object> geometry = new HashMap<>();
            geometry.put("type", "Point");
            geometry.put("coordinates", new double[]{
                    toDouble(row.get("lng")),
                    toDouble(row.get("lat"))
            });

            Map<String, Object> props = new HashMap<>();
            props.put("ogcFid",    row.get("ogc_fid"));
            props.put("nodeId",    row.get("node_id"));
            props.put("nodeType",  row.get("node_type"));
            props.put("nodeName",  row.get("node_name"));
            props.put("turnP",     row.get("turn_p"));

            Map<String, Object> feature = new HashMap<>();
            feature.put("type",       "Feature");
            feature.put("geometry",   geometry);
            feature.put("properties", props);
            return feature;
        }).toList();

        Map<String, Object> result = new HashMap<>();
        result.put("type",       "FeatureCollection");
        result.put("totalCount", features.size());
        result.put("features",   features);
        return result;
    }

    private double toDouble(Object val) {
        if (val == null) return 0.0;
        return ((Number) val).doubleValue();
    }
}
