package org.example.connectcg_be.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnlineUserServiceTest {
    private final OnlineUserService onlineUserService = new OnlineUserService();

    @Test
    void userOnlyGoesOfflineAfterLastSessionDisconnects() {
        assertTrue(onlineUserService.connect(7, "session-a"));
        assertFalse(onlineUserService.connect(7, "session-b"));

        assertFalse(onlineUserService.disconnect(7, "session-a"));
        assertTrue(onlineUserService.isUserOnline(7));

        assertTrue(onlineUserService.disconnect(7, "session-b"));
        assertFalse(onlineUserService.isUserOnline(7));
    }

    @Test
    void onlineUsersIsAnImmutableSnapshot() {
        onlineUserService.connect(7, "session-a");

        Set<Integer> snapshot = onlineUserService.getOnlineUsers();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(8));

        onlineUserService.connect(8, "session-b");
        assertFalse(snapshot.contains(8));
    }
}
