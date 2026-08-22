package org.example.connectcg_be.realtime;

public interface RealtimeEventPublisher {
    void sendToTopic(String destination, Object payload);

    void sendToUser(String username, String destination, Object payload);

    void sendEphemeralToTopic(String destination, Object payload);
}
