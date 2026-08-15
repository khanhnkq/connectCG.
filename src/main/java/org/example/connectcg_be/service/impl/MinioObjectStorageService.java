package org.example.connectcg_be.service.impl;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import lombok.RequiredArgsConstructor;
import org.example.connectcg_be.config.MinioStorageProperties;
import org.example.connectcg_be.service.ObjectStorageService;
import org.example.connectcg_be.service.StorageException;
import org.example.connectcg_be.service.StoredObject;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
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

    private synchronized void ensureBucketReady() {
        if (bucketReady.get()) return;
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(properties.getBucket())
                    .build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
            }
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(properties.getBucket())
                    .config(publicReadPolicy(properties.getBucket()))
                    .build());
            bucketReady.set(true);
        } catch (Exception exception) {
            throw new StorageException("Không thể khởi tạo MinIO bucket", exception);
        }
    }

    private String publicUrl(String objectKey) {
        String base = properties.getPublicUrl().replaceAll("/+$", "");
        return base + "/" + properties.getBucket() + "/" + objectKey;
    }

    private String publicReadPolicy(String bucket) {
        return """
                {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},"Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}
                """.formatted(bucket).trim();
    }
}
