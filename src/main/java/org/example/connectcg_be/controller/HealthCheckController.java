package org.example.connectcg_be.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthCheckController {

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;

    @Autowired
    public HealthCheckController(DataSource dataSource, RedisConnectionFactory redisConnectionFactory) {
        this.dataSource = dataSource;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @GetMapping("/readiness")
    public ResponseEntity<Map<String, Object>> checkReadiness() {
        Map<String, Object> statusMap = new HashMap<>();
        boolean dbHealthy = isDbHealthy();
        boolean redisHealthy = isRedisHealthy();

        statusMap.put("db", dbHealthy ? "UP" : "DOWN");
        statusMap.put("redis", redisHealthy ? "UP" : "DOWN");

        if (dbHealthy && redisHealthy) {
            statusMap.put("status", "UP");
            return ResponseEntity.ok(statusMap);
        } else {
            statusMap.put("status", "DOWN");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(statusMap);
        }
    }

    private boolean isDbHealthy() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isRedisHealthy() {
        try {
            var connection = redisConnectionFactory.getConnection();
            String pingResult = connection.ping();
            connection.close();
            return "PONG".equalsIgnoreCase(pingResult) || pingResult != null;
        } catch (Exception e) {
            return false;
        }
    }
}
