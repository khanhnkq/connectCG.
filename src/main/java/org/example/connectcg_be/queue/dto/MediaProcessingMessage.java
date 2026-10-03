package org.example.connectcg_be.queue.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaProcessingMessage implements Serializable {

    private String messageId;
    private Integer mediaId;
    private String objectKey;
    private String mediaType;
    private String category;
    private String contentType;
    private Instant createdAt;
}
