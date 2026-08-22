package org.example.connectcg_be.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.connectcg_be.dto.HobbyDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisJsonCacheClientTest {
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RedisJsonCacheClient cacheClient;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cacheClient = new RedisJsonCacheClient(redisTemplate, new ObjectMapper());
    }

    @Test
    void deserializesTypedLists() {
        when(valueOperations.get("hobbies")).thenReturn("""
                [{"id":1,"code":"ART","name":"Art","icon":null,"category":"CREATIVE"}]
                """);

        List<HobbyDTO> result = cacheClient.findList("hobbies", HobbyDTO.class).orElseThrow();

        assertEquals(1, result.size());
        assertEquals("ART", result.get(0).getCode());
    }

    @Test
    void redisFailureBehavesAsCacheMissAndDoesNotBlockWrites() {
        when(valueOperations.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("down"))
                .when(valueOperations).set(anyString(), anyString(), org.mockito.ArgumentMatchers.any());

        assertTrue(cacheClient.find("key", HobbyDTO.class).isEmpty());
        assertDoesNotThrow(() -> cacheClient.store("key", new HobbyDTO(), Duration.ofMinutes(1)));
    }

    @Test
    void invalidJsonIsEvictedAndTreatedAsMiss() {
        when(valueOperations.get("key")).thenReturn("not-json");

        assertTrue(cacheClient.find("key", HobbyDTO.class).isEmpty());
        verify(redisTemplate).delete("key");
    }
}
