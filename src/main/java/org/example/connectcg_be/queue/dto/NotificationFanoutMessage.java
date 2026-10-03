package org.example.connectcg_be.queue.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationFanoutMessage implements Serializable {

    private String messageId;
    private List<Integer> recipientUserIds;
    private Integer actorId;
    private String content;
    private String type;
    private String targetType;
    private Integer targetId;
    private Instant createdAt;
}
