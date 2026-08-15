package org.example.connectcg_be.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.minio")
public class MinioStorageProperties {
    private String endpoint;
    private String publicUrl;
    private String accessKey;
    private String secretKey;
    private String bucket;
}
