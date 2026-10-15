package org.example.connectcg_be.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.connectcg_be.config.AppwriteStorageProperties;
import org.example.connectcg_be.service.MediaCategory;
import org.example.connectcg_be.service.ObjectStorageService;
import org.example.connectcg_be.service.StorageException;
import org.example.connectcg_be.service.StoredObject;
import org.example.connectcg_be.util.AppwriteStorageUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "appwrite", matchIfMissing = true)
@Slf4j
public class AppwriteStorageService implements ObjectStorageService {

    private final AppwriteStorageProperties properties;
    private final RestClient restClient;

    public AppwriteStorageService(AppwriteStorageProperties properties) {
        this.properties = properties;

        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getReadTimeoutMs());

        this.restClient = RestClient.builder()
                .baseUrl(properties.getEndpoint())
                .defaultHeader("X-Appwrite-Project", properties.getProjectId())
                .defaultHeader("X-Appwrite-Key", properties.getApiKey())
                .requestFactory(requestFactory)
                .build();
    }

    public AppwriteStorageService(AppwriteStorageProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public StoredObject store(InputStream inputStream, long size, String contentType, String objectKey) {
        String fileId = AppwriteStorageUtils.toAppwriteId(objectKey);
        String safeFilename = AppwriteStorageUtils.toSafeFilename(objectKey, contentType);

        MediaCategory category = MediaCategory.fromObjectKey(objectKey);
        boolean isPublic = (category == null || category.isPublic());

        try {
            byte[] bytes = inputStream.readAllBytes();

            ByteArrayResource fileResource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return safeFilename;
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("fileId", fileId);
            body.add("file", fileResource);

            if (isPublic) {
                body.add("permissions[]", "read(\"any\")");
            }

            restClient.post()
                    .uri("/storage/buckets/{bucketId}/files", properties.getBucketId())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        String responseBody = new String(res.getBody().readAllBytes());
                        log.error("Appwrite upload failed for key [{}], status [{}]: {}",
                                objectKey, res.getStatusCode(), responseBody);
                        throw new StorageException("Không thể upload media lên Appwrite: " + responseBody);
                    })
                    .toBodilessEntity();

            String accessUrl = isPublic ? buildPublicViewUrl(fileId) : buildInternalProxyUrl(objectKey);
            log.info("Appwrite store success: fileId={} objectKey={} isPublic={} url={}",
                    fileId, objectKey, isPublic, accessUrl);

            return new StoredObject(properties.getBucketId(), objectKey, accessUrl);

        } catch (StorageException se) {
            throw se;
        } catch (Exception e) {
            log.error("Lỗi khi lưu media vào Appwrite Storage [{}]: {}", objectKey, e.getMessage(), e);
            throw new StorageException("Không thể lưu media vào Appwrite Storage", e);
        }
    }

    @Override
    public InputStream load(String objectKey) {
        String fileId = AppwriteStorageUtils.toAppwriteId(objectKey);
        try {
            byte[] fileBytes = restClient.get()
                    .uri("/storage/buckets/{bucketId}/files/{fileId}/download", properties.getBucketId(), fileId)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        String err = new String(res.getBody().readAllBytes());
                        throw new StorageException("Không thể tải file từ Appwrite (" + res.getStatusCode() + "): " + err);
                    })
                    .body(byte[].class);

            if (fileBytes == null) {
                throw new StorageException("File rỗng từ Appwrite Storage: " + objectKey);
            }
            return new ByteArrayInputStream(fileBytes);

        } catch (StorageException se) {
            throw se;
        } catch (Exception e) {
            log.error("Lỗi khi đọc file từ Appwrite Storage [{}]: {}", objectKey, e.getMessage());
            throw new StorageException("Không thể đọc media từ Appwrite Storage", e);
        }
    }

    @Override
    public void delete(String objectKey) {
        String fileId = AppwriteStorageUtils.toAppwriteId(objectKey);
        try {
            restClient.delete()
                    .uri("/storage/buckets/{bucketId}/files/{fileId}", properties.getBucketId(), fileId)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (req, res) -> {
                        log.debug("File Appwrite [{}] không tồn tại (404), bỏ qua delete.", fileId);
                    })
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        if (res.getStatusCode().value() != 404) {
                            String err = new String(res.getBody().readAllBytes());
                            log.warn("Appwrite delete error status [{}]: {}", res.getStatusCode(), err);
                            throw new StorageException("Lỗi xóa file trên Appwrite: " + err);
                        }
                    })
                    .toBodilessEntity();

            log.info("Appwrite delete success for fileId [{}] (objectKey: {})", fileId, objectKey);

        } catch (StorageException se) {
            throw se;
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("404")) {
                log.debug("File [{}] not found on Appwrite during delete, continuing.", fileId);
                return;
            }
            log.warn("Không thể xóa file Appwrite [{}]: {}", fileId, e.getMessage());
            throw new StorageException("Không thể xóa media khỏi Appwrite Storage", e);
        }
    }

    @Override
    public String getProviderName() {
        return "APPWRITE";
    }

    @Override
    public String generateThumbnailUrl(String objectKey, int width, int height) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        MediaCategory category = MediaCategory.fromObjectKey(objectKey);
        if (category != null && !category.isPublic()) {
            return null;
        }
        String fileId = AppwriteStorageUtils.toAppwriteId(objectKey);
        return buildPublicPreviewUrl(fileId, width, height);
    }

    public String buildPublicViewUrl(String fileId) {
        String base = properties.getEndpoint().replaceAll("/+$", "");
        return String.format("%s/storage/buckets/%s/files/%s/view?project=%s",
                base, properties.getBucketId(), fileId, properties.getProjectId());
    }

    public String buildPublicPreviewUrl(String fileId, int width, int height) {
        String base = properties.getEndpoint().replaceAll("/+$", "");
        return String.format("%s/storage/buckets/%s/files/%s/preview?project=%s&width=%d&height=%d&output=webp",
                base, properties.getBucketId(), fileId, properties.getProjectId(), width, height);
    }

    public String buildInternalProxyUrl(String objectKey) {
        return "/api/v1/media/view/" + objectKey;
    }
}
