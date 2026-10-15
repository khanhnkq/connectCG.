package org.example.connectcg_be.config;

import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "minio")
@EnableConfigurationProperties(MinioStorageProperties.class)
public class MinioConfig {

    @Bean
    public MinioClient minioClient(MinioStorageProperties properties) {
        var builder = MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey());

        if (properties.getRegion() != null && !properties.getRegion().isBlank()) {
            builder.region(properties.getRegion());
        }

        return builder.build();
    }
}
