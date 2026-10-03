package org.example.connectcg_be.queue.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiModerationMessage implements Serializable {
    @Builder.Default
    private String messageId = UUID.randomUUID().toString();
    private Integer postId;
    private String content;
    private String actionType; // "CREATE", "UPDATE", "SHARE"
    private Integer authorId;
    private Instant updatedAt;
    @Builder.Default
    private Instant createdAt = Instant.now();
}
