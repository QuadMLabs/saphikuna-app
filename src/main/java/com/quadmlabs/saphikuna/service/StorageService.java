package com.quadmlabs.saphikuna.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    String upload(MultipartFile file, String path);

    String getPublicUrl(String path);

    void delete(String path);
}
