package com.echcherqaoui.jobboard.userservice.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.cloudinary.utils.ObjectUtils;
import com.echcherqaoui.jobboard.userservice.exception.domain.CvStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static com.echcherqaoui.jobboard.userservice.exception.enums.UserErrorCode.CV_DELETE_FAILED;
import static com.echcherqaoui.jobboard.userservice.exception.enums.UserErrorCode.CV_UPLOAD_FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudinaryCvClientTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    @InjectMocks
    private CloudinaryCvClient cloudinaryCvClient;

    @BeforeEach
    void setUp() {
        when(cloudinary.uploader()).thenReturn(uploader);
    }

    @Nested
    class UploadCv {

        @Test
        void uploadCv_WhenSuccessful_ShouldReturnCvUploadResult() throws IOException {
            UUID userId = UUID.randomUUID();
            byte[] fileContent = "PDF content".getBytes();
            MultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", fileContent);

            Map<String, Object> expectedOptions = Map.of(
                  "resource_type", "auto",
                  "folder", "cvs",
                  "public_id", userId.toString(),
                  "overwrite", true
            );

            Map<String, Object> cloudinaryResponse = Map.of(
                  "secure_url", "https://res.cloudinary.com/demo/image/upload/v12345/cvs/resume.pdf",
                  "public_id", "cvs/" + userId
            );

            when(uploader.upload(fileContent, expectedOptions)).thenReturn(cloudinaryResponse);

            CvUploadResult result = cloudinaryCvClient.uploadCv(file, userId);

            assertThat(result).isNotNull();
            assertThat(result.url()).isEqualTo("https://res.cloudinary.com/demo/image/upload/v12345/cvs/resume.pdf");
            assertThat(result.publicId()).isEqualTo("cvs/" + userId);

            verify(uploader).upload(fileContent, expectedOptions);
        }

        @Test
        void uploadCv_WhenIoExceptionOccurs_ShouldThrowCvStorageException() throws IOException {
            UUID userId = UUID.randomUUID();
            MultipartFile file = mock(MultipartFile.class);

            when(file.getBytes()).thenThrow(new IOException("Disk read error"));

            assertThatThrownBy(() -> cloudinaryCvClient.uploadCv(file, userId))
                  .isInstanceOf(CvStorageException.class)
                  .extracting("errorCode")
                  .isEqualTo(CV_UPLOAD_FAILED);
        }

        @Test
        void uploadCv_WhenCloudinaryThrowsRuntimeException_ShouldWrapInCvStorageException() throws IOException {
            UUID userId = UUID.randomUUID();
            byte[] fileContent = "PDF content".getBytes();
            MultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", fileContent);

            when(uploader.upload(eq(fileContent), anyMap()))
                  .thenThrow(new RuntimeException("Cloudinary API unavailable"));

            assertThatThrownBy(() -> cloudinaryCvClient.uploadCv(file, userId))
                  .isInstanceOf(CvStorageException.class)
                  .extracting("errorCode")
                  .isEqualTo(CV_UPLOAD_FAILED);
        }
    }

    @Nested
    class DeleteCv {

        @Test
        void deleteCv_WhenStatusIsOk_ShouldSucceedWithoutException() throws IOException {
            String publicId = "cvs/user-123";
            Map<String, Object> cloudinaryResponse = Map.of("result", "ok");

            when(uploader.destroy(publicId, ObjectUtils.emptyMap()))
                  .thenReturn(cloudinaryResponse);

            cloudinaryCvClient.deleteCv(publicId);

            verify(uploader).destroy(publicId, ObjectUtils.emptyMap());
        }

        @Test
        void deleteCv_WhenResultNotOk_ShouldThrowCvStorageException() throws IOException {
            String publicId = "cvs/user-123";
            Map<String, Object> cloudinaryResponse = Map.of("result", "not found");

            when(uploader.destroy(publicId, ObjectUtils.emptyMap()))
                  .thenReturn(cloudinaryResponse);

            assertThatThrownBy(() -> cloudinaryCvClient.deleteCv(publicId))
                  .isInstanceOf(CvStorageException.class)
                  .extracting("errorCode")
                  .isEqualTo(CV_DELETE_FAILED);
        }

        @Test
        void deleteCv_WhenCloudinaryThrowsException_ShouldWrapInCvStorageException() throws IOException {
            String publicId = "cvs/user-123";

            when(uploader.destroy(publicId, ObjectUtils.emptyMap()))
                  .thenThrow(new IOException("Network timeout"));

            assertThatThrownBy(() -> cloudinaryCvClient.deleteCv(publicId))
                  .isInstanceOf(CvStorageException.class)
                  .extracting("errorCode")
                  .isEqualTo(CV_DELETE_FAILED);
        }
    }
}