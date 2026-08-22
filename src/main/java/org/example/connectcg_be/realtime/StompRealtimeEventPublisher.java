package org.example.connectcg_be.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
        messagingTemplate.convertAndSend(destination, payload);
    }

    private void afterCommitOrNow(Runnable sendAction) {
        if (!isWriteTransaction()) {
            sendAction.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendAction.run();
            }
        });
    }

    private boolean isWriteTransaction() {
        return TransactionSynchronizationManager.isSynchronizationActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly();
    }
}
