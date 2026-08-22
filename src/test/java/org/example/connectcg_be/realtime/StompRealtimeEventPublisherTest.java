package org.example.connectcg_be.realtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class StompRealtimeEventPublisherTest {
    private final SimpMessageSendingOperations messagingTemplate = mock(SimpMessageSendingOperations.class);
    private final StompRealtimeEventPublisher publisher = new StompRealtimeEventPublisher(messagingTemplate);

    @AfterEach
    void cleanTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
    }

    @Test
    void durableEventIsOnlySentAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);

        publisher.sendToTopic("/topic/posts", "payload");
        verify(messagingTemplate, never()).convertAndSend("/topic/posts", "payload");

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        verify(messagingTemplate).convertAndSend("/topic/posts", "payload");
    }

    @Test
    void ephemeralEventIsSentImmediatelyInsideTransaction() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);

        publisher.sendEphemeralToTopic("/topic/chat/key/typing", "payload");

        verify(messagingTemplate).convertAndSend("/topic/chat/key/typing", "payload");
    }

    @Test
    void durableEventIsNotSentWhenCommitDoesNotHappen() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);

        publisher.sendToUser("john", "/queue/chat", "payload");

        verify(messagingTemplate, never()).convertAndSendToUser("john", "/queue/chat", "payload");
    }
}
