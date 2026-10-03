package org.example.connectcg_be.controller;

import org.example.connectcg_be.dto.TungNotificationDTO;
import org.example.connectcg_be.security.UserPrincipal;
import org.example.connectcg_be.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TungNotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private TungNotificationController controller;

    private Authentication authentication;
    private UserPrincipal userPrincipal;

    @BeforeEach
    void setUp() {
        userPrincipal = new UserPrincipal(10, "testuser", "test@example.com", "pass", true, false, false, List.of());
        authentication = new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities());
    }

    @Test
    void getMyNotifications_returnsListFromService() {
        TungNotificationDTO dto = new TungNotificationDTO();
        dto.setId(1);
        dto.setContent("Test noti");
        when(notificationService.getMyNotifications(10, 0, 20)).thenReturn(List.of(dto));

        List<TungNotificationDTO> result = controller.getMyNotifications(authentication, 0, 20);

        assertEquals(1, result.size());
        assertEquals("Test noti", result.get(0).getContent());
        verify(notificationService).getMyNotifications(10, 0, 20);
    }

    @Test
    void getMyNotifications_customPagination_passesParamsToService() {
        when(notificationService.getMyNotifications(10, 2, 15)).thenReturn(List.of());

        List<TungNotificationDTO> result = controller.getMyNotifications(authentication, 2, 15);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(notificationService).getMyNotifications(10, 2, 15);
    }

    @Test
    void markAllAsRead_callsServiceAndReturnsOk() {
        ResponseEntity<Void> response = controller.markAllAsRead(authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(notificationService).markAllAsRead(10);
    }

    @Test
    void markAsRead_callsServiceAndReturnsOk() {
        ResponseEntity<Void> response = controller.markAsRead(42, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(notificationService).markAsRead(42, 10);
    }

    @Test
    void deleteNotification_callsServiceAndReturnsOk() {
        ResponseEntity<Void> response = controller.deleteNotification(42, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(notificationService).deleteNotification(42, 10);
    }
}
