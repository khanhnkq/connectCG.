package org.example.connectcg_be.service;

import org.example.connectcg_be.dto.AiModerationResult;
import java.util.Collections;
import java.util.List;

public interface AiModerationService {
    AiModerationResult checkPostContent(String content);

    default AiModerationResult checkPostContent(String content, List<String> mediaUrls) {
        return checkPostContent(content);
    }
}

