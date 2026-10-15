package org.example.connectcg_be.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.appwrite")
public class AppwriteStorageProperties {

    /**
     * Appwrite REST API Endpoint (mặc định Frankfurt Cloud).
     */
    private String endpoint = "https://fra.cloud.appwrite.io/v1";

    /**
     * ID dự án Appwrite (mặc định: backend-storage-hub).
     */
    private String projectId = "backend-storage-hub";

    /**
     * ID của bucket lưu trữ media ConnectCG.
     */
    private String bucketId = "connect-storage";

    /**
     * Secret API Key có quyền files.read và files.write.
     */
    private String apiKey;

    /**
     * Timeout kết nối HTTP tính bằng milliseconds (mặc định 5s).
     */
    private int connectTimeoutMs = 5000;

    /**
     * Timeout đọc dữ liệu HTTP tính bằng milliseconds (mặc định 30s).
     */
    private int readTimeoutMs = 30000;
}
