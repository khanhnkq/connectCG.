package org.example.connectcg_be.service;

import java.io.InputStream;

public interface ObjectStorageService {
    StoredObject store(InputStream inputStream, long size, String contentType, String objectKey);

    void delete(String objectKey);
}
