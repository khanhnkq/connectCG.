package org.example.connectcg_be.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompRealtimeEventPublisher implements RealtimeEventPublisher {
    private final SimpMessageSendingOperations messagingTemplate;

    @Override
    public void sendToTopic(String destination, Object payload) {
        afterCommitOrNow(() -> messagingTemplate.convertAndSend(destination, payload));
    }

    @Override
    public void sendToUser(String username, String destination, Object payload) {
        afterCommitOrNow(() -> messagingTemplate.convertAndSendToUser(username, destination, payload));
    }

    @Override
    public void sendEphemeralToTopic(String destination, Object payload) {
        executeSafely(() -> messagingTemplate.convertAndSend(destination, payload));
    }

    private void afterCommitOrNow(Runnable sendAction) {
        if (!isWriteTransaction()) {
            executeSafely(sendAction);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                executeSafely(sendAction);
            }
        });
    }

    private void executeSafely(Runnable sendAction) {
        try {
            sendAction.run();
        } catch (Exception e) {
            log.error("Failed to send realtime event: {}", e.getMessage(), e);
        }
    }

    private boolean isWriteTransaction() {
        return TransactionSynchronizationManager.isSynchronizationActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly();
    }
}

