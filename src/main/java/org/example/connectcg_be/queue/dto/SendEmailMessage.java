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
public class SendEmailMessage implements Serializable {
    @Builder.Default
    private String messageId = UUID.randomUUID().toString();
    private String to;
    private String subject;
    private String htmlBody;
    private String templateType;
    @Builder.Default
    private Instant createdAt = Instant.now();
}
