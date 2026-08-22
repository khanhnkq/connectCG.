package org.example.connectcg_be.service;

import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class OnlineUserService {

    private final ConcurrentMap<Integer, Set<String>> sessionsByUser = new ConcurrentHashMap<>();

    public boolean connect(Integer userId, String sessionId) {
        AtomicBoolean becameOnline = new AtomicBoolean(false);
        sessionsByUser.compute(userId, (id, sessions) -> {
            Set<String> currentSessions = sessions != null ? sessions : ConcurrentHashMap.newKeySet();
            if (currentSessions.add(sessionId) && currentSessions.size() == 1) {
                becameOnline.set(true);
            }
            return currentSessions;
        });
        return becameOnline.get();
    }

    public boolean disconnect(Integer userId, String sessionId) {
        AtomicBoolean becameOffline = new AtomicBoolean(false);
        sessionsByUser.computeIfPresent(userId, (id, sessions) -> {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                becameOffline.set(true);
                return null;
            }
            return sessions;
        });
        return becameOffline.get();
    }

    public boolean isUserOnline(Integer userId) {
        return sessionsByUser.containsKey(userId);
    }

    public Set<Integer> getOnlineUsers() {
        return Set.copyOf(sessionsByUser.keySet());
    }
}
