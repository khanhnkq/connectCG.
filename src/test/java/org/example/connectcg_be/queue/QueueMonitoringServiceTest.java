package org.example.connectcg_be.queue;

import org.example.connectcg_be.config.RabbitMQConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueueMonitoringServiceTest {

    @Mock
    private RabbitAdmin rabbitAdmin;

    private QueueMonitoringService monitoringService;

    @BeforeEach
    void setUp() {
        monitoringService = new QueueMonitoringService(rabbitAdmin);
        ReflectionTestUtils.setField(monitoringService, "queueEnabled", true);
    }

    @Test
    @DisplayName("Should return empty status when queue is disabled")
    void shouldReturnDisabledStatusWhenQueueDisabled() {
        ReflectionTestUtils.setField(monitoringService, "queueEnabled", false);

        QueueMonitoringService.QueueClusterStatus status = monitoringService.getQueueStatus();

        assertFalse(status.isQueueEnabled());
        assertFalse(status.isConnected());
        assertEquals(0, status.getDeadLetterCount());
        assertFalse(status.isHasDlqAlert());
        verifyNoInteractions(rabbitAdmin);
    }

    @Test
    @DisplayName("Should gather queue metrics and detect DLQ alert when count > 10")
    void shouldDetectDlqAlertWhenCountExceedsThreshold() {
        Properties normalProps = new Properties();
        normalProps.put(RabbitAdmin.QUEUE_MESSAGE_COUNT, 5);
        normalProps.put(RabbitAdmin.QUEUE_CONSUMER_COUNT, 2);

        Properties dlqProps = new Properties();
        dlqProps.put(RabbitAdmin.QUEUE_MESSAGE_COUNT, 15);
        dlqProps.put(RabbitAdmin.QUEUE_CONSUMER_COUNT, 1);

        when(rabbitAdmin.getQueueProperties(RabbitMQConfig.QUEUE_EMAIL)).thenReturn(normalProps);
        when(rabbitAdmin.getQueueProperties(RabbitMQConfig.QUEUE_AI)).thenReturn(normalProps);
        when(rabbitAdmin.getQueueProperties(RabbitMQConfig.QUEUE_MEDIA)).thenReturn(normalProps);
        when(rabbitAdmin.getQueueProperties(RabbitMQConfig.QUEUE_NOTIFICATION)).thenReturn(normalProps);
        when(rabbitAdmin.getQueueProperties(RabbitMQConfig.QUEUE_DEAD_LETTER)).thenReturn(dlqProps);

        QueueMonitoringService.QueueClusterStatus status = monitoringService.getQueueStatus();

        assertTrue(status.isQueueEnabled());
        assertTrue(status.isConnected());
        assertEquals(5, status.getQueues().size());
        assertEquals(15, status.getDeadLetterCount());
        assertTrue(status.isHasDlqAlert());
        assertNotNull(status.getAlertMessage());
    }

    @Test
    @DisplayName("Should handle RabbitAdmin exception gracefully and mark connected=false")
    void shouldHandleRabbitAdminExceptionGracefully() {
        when(rabbitAdmin.getQueueProperties(anyString())).thenThrow(new RuntimeException("Connection timeout"));

        QueueMonitoringService.QueueClusterStatus status = monitoringService.getQueueStatus();

        assertTrue(status.isQueueEnabled());
        assertFalse(status.isConnected());
    }
}
