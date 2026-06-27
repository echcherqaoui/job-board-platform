package com.echcherqaoui.jobboard.userservice.storage;

import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface CvStorageClient {
    CvUploadResult uploadCv(MultipartFile file, UUID userId);

    void deleteCv(String publicId);
}