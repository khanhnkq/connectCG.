package org.example.connectcg_be.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

@Component
public class PublicProfileCache {
    private final RedisJsonCacheClient cacheClient;
    private final String keyPrefix;
    private final boolean enabled;
    private final Duration ttl;

    public PublicProfileCache(
            RedisJsonCacheClient cacheClient,
            @Value("${app.environment:local}") String environment,
            @Value("${app.public-profile-cache.enabled:true}") boolean enabled,
            @Value("${app.public-profile-cache.ttl:5m}") Duration ttl) {
        this.cacheClient = cacheClient;
        this.keyPrefix = "connect:%s:public-profile:v1:".formatted(environment.trim().toLowerCase(Locale.ROOT));
        this.enabled = enabled;
        this.ttl = ttl;
    }

    public Optional<PublicProfileFragment> find(Integer userId) {
        return enabled
                ? cacheClient.find(key(userId), PublicProfileFragment.class)
                : Optional.empty();
    }

    public void store(PublicProfileFragment fragment) {
        if (!enabled) {
            return;
        }
        runAfterWriteCommit(() -> cacheClient.store(key(fragment.userId()), fragment, ttl));
    }

    public void invalidate(Integer userId) {
        if (!enabled) {
            return;
        }
        String key = key(userId);
        cacheClient.evict(key);
        if (isWriteTransaction()) {
            afterCommit(() -> cacheClient.evict(key));
        }
    }

    private void runAfterWriteCommit(Runnable action) {
        if (isWriteTransaction()) {
            afterCommit(action);
        } else {
            action.run();
        }
    }

    private boolean isWriteTransaction() {
        return TransactionSynchronizationManager.isSynchronizationActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly();
    }

    private void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private String key(Integer userId) {
        return keyPrefix + userId;
    }
}
