package org.example.connectcg_be.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class RedisJsonCacheClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisJsonCacheClient.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean redisUnavailable = new AtomicBoolean(false);

    public RedisJsonCacheClient(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public <T> Optional<T> find(String key, Class<T> valueType) {
        return find(key, objectMapper.getTypeFactory().constructType(valueType));
    }

    public <T> Optional<List<T>> findList(String key, Class<T> elementType) {
        JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
        return find(key, listType);
    }

    public void store(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
            markAvailable();
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Could not serialize Redis cache value: {}", exception.getMessage());
        } catch (DataAccessException exception) {
            markUnavailable("write", exception);
        }
    }

    public void evict(String key) {
        try {
            redisTemplate.delete(key);
            markAvailable();
        } catch (DataAccessException exception) {
            markUnavailable("eviction", exception);
        }
    }

    private <T> Optional<T> find(String key, JavaType valueType) {
        try {
            String cached = redisTemplate.opsForValue().get(key);
            markAvailable();
            if (cached == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(cached, valueType));
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Invalid Redis JSON cache entry; evicting key");
            evict(key);
            return Optional.empty();
        } catch (DataAccessException exception) {
            markUnavailable("read", exception);
            return Optional.empty();
        }
    }

    private void markUnavailable(String operation, DataAccessException exception) {
        if (redisUnavailable.compareAndSet(false, true)) {
            LOGGER.warn("Redis JSON cache {} unavailable: {}", operation, exception.getMessage());
        }
    }

    private void markAvailable() {
        if (redisUnavailable.compareAndSet(true, false)) {
            LOGGER.info("Redis JSON cache available again");
        }
    }
}
