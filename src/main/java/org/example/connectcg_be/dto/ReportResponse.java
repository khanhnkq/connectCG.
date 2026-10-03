package org.example.connectcg_be.dto;

import lombok.Data;
import java.time.Instant;

@Data
public class ReportResponse {
    private Integer id;
    private Integer reporterId;
    private String targetType;
    private Integer targetId;
    private String reason;
    private String status;
    private String reporterUsername;
    private String reviewerUsername;
    private Integer groupId; // Added for frontend navigation
    private String adminNote;
    private Instant createdAt;
    private Instant resolvedAt;
}
