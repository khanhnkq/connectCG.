package org.example.connectcg_be.queue;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.connectcg_be.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueMonitoringService {

    private final RabbitAdmin rabbitAdmin;

    @Value("${app.queue.enabled:true}")
    private boolean queueEnabled;

    @Data
    @Builder
    public static class QueueMetric {
        private String queueName;
        private int messageCount;
        private int consumerCount;
    }

    @Data
    @Builder
    public static class QueueClusterStatus {
        private boolean queueEnabled;
        private boolean connected;
        private List<QueueMetric> queues;
        private int deadLetterCount;
        private boolean hasDlqAlert;
        private String alertMessage;
    }

    public QueueClusterStatus getQueueStatus() {
        if (!queueEnabled) {
            return QueueClusterStatus.builder()
                    .queueEnabled(false)
                    .connected(false)
                    .queues(Collections.emptyList())
                    .deadLetterCount(0)
                    .hasDlqAlert(false)
                    .alertMessage("Queue is disabled in application properties")
                    .build();
        }

        List<String> monitoredQueues = List.of(
                RabbitMQConfig.QUEUE_EMAIL,
                RabbitMQConfig.QUEUE_AI,
                RabbitMQConfig.QUEUE_MEDIA,
                RabbitMQConfig.QUEUE_VIDEO,
                RabbitMQConfig.QUEUE_NOTIFICATION,
                RabbitMQConfig.QUEUE_DEAD_LETTER
        );

        List<QueueMetric> metrics = new ArrayList<>();
        int dlqCount = 0;
        boolean connected = true;

        try {
            for (String queueName : monitoredQueues) {
                Properties props = rabbitAdmin.getQueueProperties(queueName);
                if (props != null) {
                    int messages = (int) props.getOrDefault(RabbitAdmin.QUEUE_MESSAGE_COUNT, 0);
                    int consumers = (int) props.getOrDefault(RabbitAdmin.QUEUE_CONSUMER_COUNT, 0);
                    metrics.add(QueueMetric.builder()
                            .queueName(queueName)
                            .messageCount(messages)
                            .consumerCount(consumers)
                            .build());

                    if (RabbitMQConfig.QUEUE_DEAD_LETTER.equals(queueName)) {
                        dlqCount = messages;
                    }
                } else {
                    metrics.add(QueueMetric.builder()
                            .queueName(queueName)
                            .messageCount(0)
                            .consumerCount(0)
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("Could not retrieve RabbitMQ queue properties: {}", e.getMessage());
            connected = false;
        }

        boolean hasAlert = dlqCount > 10;
        String alert = hasAlert ? String.format("WARNING: Dead letter queue has %d failed messages! Needs admin investigation.", dlqCount) : null;

        return QueueClusterStatus.builder()
                .queueEnabled(true)
                .connected(connected)
                .queues(metrics)
                .deadLetterCount(dlqCount)
                .hasDlqAlert(hasAlert)
                .alertMessage(alert)
                .build();
    }
}
