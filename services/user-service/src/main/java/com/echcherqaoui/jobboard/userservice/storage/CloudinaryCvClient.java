package com.echcherqaoui.jobboard.userservice.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.echcherqaoui.jobboard.userservice.exception.domain.CvStorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static com.echcherqaoui.jobboard.userservice.exception.enums.UserErrorCode.CV_DELETE_FAILED;
import static com.echcherqaoui.jobboard.userservice.exception.enums.UserErrorCode.CV_UPLOAD_FAILED;

@Component
@RequiredArgsConstructor
@Slf4j
public class CloudinaryCvClient implements CvStorageClient {

    private final Cloudinary cloudinary;

    @Override
    public CvUploadResult uploadCv(MultipartFile file, UUID userId) {
            Map<String, Object> uploadOptions = Map.of(
                  "resource_type", "auto",
                  "folder", "cvs",
                  "public_id", userId.toString(),
                  "overwrite", true
            );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(file.getBytes(), uploadOptions);

            String url = (String) result.get("secure_url");
            String publicId = (String) result.get("public_id");

            return new CvUploadResult(url, publicId);

        } catch (IOException | RuntimeException e) {
            log.error("Failed to upload CV to Cloudinary for user {}", userId, e);
            throw new CvStorageException(CV_UPLOAD_FAILED);
        }
    }

    @Override
    public void deleteCv(String publicId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());

            //Explicitly checking Cloudinary's response contract for actual deletion status
            if (!"ok".equals(result.get("result"))) {
                log.error("Cloudinary failed to destroy resource {}. Response: {}", publicId, result);
                throw new CvStorageException(CV_DELETE_FAILED, "Cloudinary returned status: " + result.get("result"));
            }

            log.info("Successfully deleted CV from Cloudinary: {}", publicId);
        } catch (IOException | RuntimeException e) {
            log.error("Cloudinary execution failed for publicId={}", publicId, e);
            throw new CvStorageException(CV_DELETE_FAILED, e, e.getMessage());
        }
    }
}