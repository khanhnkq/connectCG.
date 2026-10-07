package org.example.connectcg_be.service.impl;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.connectcg_be.config.MinioStorageProperties;
import org.example.connectcg_be.service.ObjectStorageService;
import org.example.connectcg_be.service.StorageException;
import org.example.connectcg_be.service.StoredObject;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioObjectStorageService implements ObjectStorageService {
    private final MinioClient minioClient;
    private final MinioStorageProperties properties;
    private final AtomicBoolean bucketReady = new AtomicBoolean(false);

    @Override
    public StoredObject store(InputStream inputStream, long size, String contentType, String objectKey) {
        ensureBucketReady();
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .stream(inputStream, size, -1L)
                    .contentType(contentType)
                    .build());
            return new StoredObject(properties.getBucket(), objectKey, publicUrl(objectKey));
        } catch (Exception exception) {
            throw new StorageException("Không thể lưu media vào object storage", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            throw new StorageException("Không thể xóa media khỏi object storage", exception);
        }
    }

    @Override
    public InputStream load(String objectKey) {
        try {
            return minioClient.getObject(io.minio.GetObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            log.error("Failed to load object {} from bucket {}: {}", objectKey, properties.getBucket(), exception.getMessage());
            throw new StorageException("Không thể đọc media từ object storage", exception);
        }
    }

    private synchronized void ensureBucketReady() {
        if (bucketReady.get()) return;
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(properties.getBucket())
                    .build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
            }
            try {
                minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                        .bucket(properties.getBucket())
                        .config(publicReadPolicy(properties.getBucket()))
                        .build());
            } catch (Exception ignored) {
                // S3 providers like Garage manage public access via bucket website rather than bucket policies
            }
            bucketReady.set(true);
        } catch (Exception exception) {
            log.error("ensureBucketReady failed for bucket {}: {}", properties.getBucket(), exception.getMessage(), exception);
            throw new StorageException("Không thể khởi tạo MinIO bucket", exception);
        }
    }

    private String publicUrl(String objectKey) {
        String base = properties.getPublicUrl().replaceAll("/+$", "");
        if (base.contains("/api/v1/media/view")) {
            return base + "/" + objectKey;
        }
        return base + "/api/v1/media/view/" + objectKey;
    }

    private String publicReadPolicy(String bucket) {
        return """
                {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},"Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/avatar/*","arn:aws:s3:::%s/cover/*","arn:aws:s3:::%s/post/*","arn:aws:s3:::%s/comment/*","arn:aws:s3:::%s/group/*"]}]}
                """.formatted(bucket, bucket, bucket, bucket, bucket).trim();
    }
}
