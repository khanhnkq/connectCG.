package org.example.connectcg_be.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HealthCheckControllerTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private RedisConnectionFactory redisConnectionFactory;

    @Mock
    private Connection dbConnection;

    @Mock
    private RedisConnection redisConnection;

    private HealthCheckController controller;

    @BeforeEach
    void setUp() {
        controller = new HealthCheckController(dataSource, redisConnectionFactory);
    }

    @Test
    void checkReadiness_WhenDbAndRedisUp_Returns200OK() throws SQLException {
        when(dataSource.getConnection()).thenReturn(dbConnection);
        when(dbConnection.isValid(anyInt())).thenReturn(true);
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");

        ResponseEntity<Map<String, Object>> response = controller.checkReadiness();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().get("status"));
        assertEquals("UP", response.getBody().get("db"));
        assertEquals("UP", response.getBody().get("redis"));

        verify(redisConnection).close();
    }

    @Test
    void checkReadiness_WhenDbDown_Returns503ServiceUnavailable() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused"));
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");

        ResponseEntity<Map<String, Object>> response = controller.checkReadiness();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("DOWN", response.getBody().get("status"));
        assertEquals("DOWN", response.getBody().get("db"));
        assertEquals("UP", response.getBody().get("redis"));
    }

    @Test
    void checkReadiness_WhenRedisDown_Returns503ServiceUnavailable() throws SQLException {
        when(dataSource.getConnection()).thenReturn(dbConnection);
        when(dbConnection.isValid(anyInt())).thenReturn(true);
        when(redisConnectionFactory.getConnection()).thenThrow(new RuntimeException("Redis connection failed"));

        ResponseEntity<Map<String, Object>> response = controller.checkReadiness();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("DOWN", response.getBody().get("status"));
        assertEquals("UP", response.getBody().get("db"));
        assertEquals("DOWN", response.getBody().get("redis"));
    }
}
