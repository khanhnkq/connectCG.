package org.example.connectcg_be.cache;

import org.example.connectcg_be.dto.HobbyDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class HobbyCache {
    private final RedisJsonCacheClient cacheClient;
    private final String key;
    private final boolean enabled;
    private final Duration ttl;

    public HobbyCache(
            RedisJsonCacheClient cacheClient,
            @Value("${app.environment:local}") String environment,
            @Value("${app.hobby-cache.enabled:true}") boolean enabled,
            @Value("${app.hobby-cache.ttl:6h}") Duration ttl) {
        this.cacheClient = cacheClient;
        this.key = "connect:%s:reference:v1:hobbies".formatted(environment.trim().toLowerCase(Locale.ROOT));
        this.enabled = enabled;
        this.ttl = ttl;
    }

    public Optional<List<HobbyDTO>> find() {
        return enabled ? cacheClient.findList(key, HobbyDTO.class) : Optional.empty();
    }

    public void store(List<HobbyDTO> hobbies) {
        if (enabled) {
            cacheClient.store(key, hobbies, ttl);
        }
    }
}
