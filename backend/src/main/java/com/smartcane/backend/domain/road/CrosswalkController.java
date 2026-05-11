package com.smartcane.backend.domain.road;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ConditionalOnExpression("'${app.datasource.postgres.host:}' != ''")
@RestController
@RequestMapping("/road")
public class CrosswalkController {

    private final JdbcTemplate jdbc;

    public CrosswalkController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/crosswalks")
    public Map<String, Object> getCrosswalks(
            @RequestParam double minLng,
            @RequestParam double minLat,
            @RequestParam double maxLng,
            @RequestParam double maxLat,
            @RequestParam(defaultValue = "2000") int limit
    ) {
        String sql = """
                SELECT id, mgmt_no, lat, lng
                FROM crosswalks
                WHERE lng BETWEEN ? AND ?
                  AND lat BETWEEN ? AND ?
                LIMIT ?
                """;

        List<Map<String, Object>> rows = jdbc.queryForList(sql, minLng, maxLng, minLat, maxLat, limit);

        List<Map<String, Object>> features = rows.stream().map(row -> {
            Map<String, Object> geometry = new HashMap<>();
            geometry.put("type", "Point");
            geometry.put("coordinates", new double[]{
                    toDouble(row.get("lng")),
                    toDouble(row.get("lat"))
            });

            Map<String, Object> props = new HashMap<>();
            props.put("id",     row.get("id"));
            props.put("mgmtNo", row.get("mgmt_no"));

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
