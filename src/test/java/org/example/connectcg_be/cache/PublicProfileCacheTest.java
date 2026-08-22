package org.example.connectcg_be.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PublicProfileCacheTest {
    @AfterEach
    void cleanTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
    }

    @Test
    void writeTransactionEvictsImmediatelyAndStoresFreshFragmentAfterCommit() {
        RedisJsonCacheClient cacheClient = mock(RedisJsonCacheClient.class);
        PublicProfileCache cache = new PublicProfileCache(
                cacheClient, "test", true, Duration.ofMinutes(5));
        PublicProfileFragment fragment = new PublicProfileFragment(7, "New Name", "avatar", "cover");
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);

        cache.invalidate(7);
        cache.store(fragment);

        verify(cacheClient).evict("connect:test:public-profile:v1:7");
        verify(cacheClient, never()).store(
                "connect:test:public-profile:v1:7", fragment, Duration.ofMinutes(5));

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        verify(cacheClient, times(2)).evict("connect:test:public-profile:v1:7");
        verify(cacheClient).store(
                "connect:test:public-profile:v1:7", fragment, Duration.ofMinutes(5));
    }
}
