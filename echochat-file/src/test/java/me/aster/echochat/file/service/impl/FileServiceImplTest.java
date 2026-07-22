package me.aster.echochat.file.service.impl;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import me.aster.echochat.common.exception.BusinessException;
import me.aster.echochat.common.result.ResultCode;
import me.aster.echochat.common.util.SnowflakeIdGenerator;
import me.aster.echochat.file.entity.FileMeta;
import me.aster.echochat.file.mapper.FileMetaMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FileServiceImpl")
class FileServiceImplTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private FileMetaMapper fileMetaMapper;

    @InjectMocks
    private FileServiceImpl fileService;

    @Nested
    @DisplayName("upload")
    class UploadTests {

        @Mock
        private MultipartFile multipartFile;

        @BeforeEach
        void setUp() throws IOException {
            when(multipartFile.getOriginalFilename()).thenReturn("test.pdf");
            when(multipartFile.getSize()).thenReturn(1024L);
            when(multipartFile.getContentType()).thenReturn("application/pdf");
            when(multipartFile.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[1024]));
        }

        @Test
        @DisplayName("should upload file successfully and persist metadata")
        void shouldUploadFileSuccessfully() throws Exception {
            when(snowflakeIdGenerator.nextId()).thenReturn(123456L);
            when(fileMetaMapper.insert(any(FileMeta.class))).thenReturn(1);
            when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(null);

            FileMeta result = fileService.upload(1001L, multipartFile, "pdf", "file");

            assertThat(result).isNotNull();
            assertThat(result.getFileId()).isEqualTo(123456L);
            assertThat(result.getUid()).isEqualTo(1001L);
            assertThat(result.getBucket()).isEqualTo("files");
            assertThat(result.getSize()).isEqualTo(1024L);
            assertThat(result.getExt()).isEqualTo("pdf");
            verify(fileMetaMapper).insert(any(FileMeta.class));
        }

        @Test
        @DisplayName("should strip leading dot from extension")
        void shouldStripLeadingDotFromExtension() throws Exception {
            when(snowflakeIdGenerator.nextId()).thenReturn(123456L);
            when(fileMetaMapper.insert(any(FileMeta.class))).thenReturn(1);
            when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(null);

            FileMeta result = fileService.upload(1001L, multipartFile, ".png", "image");

            assertThat(result).isNotNull();
            assertThat(result.getExt()).isEqualTo("png");
            assertThat(result.getBucket()).isEqualTo("images");
        }

        @Test
        @DisplayName("should throw when extension is unsupported for type")
        void shouldThrowForUnsupportedExtension() {
            assertThatThrownBy(() -> fileService.upload(1001L, multipartFile, "exe", "file"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported file type");
        }

        @Test
        @DisplayName("should throw when uid is null and UserContext is not set")
        void shouldThrowWhenUidIsNull() {
            assertThatThrownBy(() -> fileService.upload(null, multipartFile, "pdf", "file"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("User ID is required");
        }

        @Test
        @DisplayName("should throw BusinessException when MinIO putObject fails")
        void shouldThrowBusinessExceptionWhenMinioPutFails() throws Exception {
            when(snowflakeIdGenerator.nextId()).thenReturn(123456L);
            when(minioClient.putObject(any(PutObjectArgs.class)))
                    .thenThrow(new RuntimeException("MinIO connection error"));

            assertThatThrownBy(() -> fileService.upload(1001L, multipartFile, "pdf", "file"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("File upload failed");
        }
    }

    @Nested
    @DisplayName("getDownloadUrl")
    class GetDownloadUrlTests {

        private FileMeta fileMeta;

        @BeforeEach
        void setUp() {
            fileMeta = new FileMeta();
            fileMeta.setFileId(1L);
            fileMeta.setBucket("files");
            fileMeta.setObjectPath("2024-05/1001/123456.pdf");
        }

        @Test
        @DisplayName("should return presigned download URL")
        void shouldReturnPresignedDownloadUrl() throws Exception {
            when(fileMetaMapper.selectById(1L)).thenReturn(fileMeta);
            when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                    .thenReturn("http://minio.example.com/files/2024-05/1001/123456.pdf?signature=xyz");

            String url = fileService.getDownloadUrl(1L);

            assertThat(url).isNotNull().contains("signature=xyz");
            verify(minioClient).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
        }

        @Test
        @DisplayName("should throw when file not found")
        void shouldThrowWhenFileNotFound() {
            when(fileMetaMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> fileService.getDownloadUrl(999L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("File not found");
        }

        @Test
        @DisplayName("should throw BusinessException when presigned URL generation fails")
        void shouldThrowWhenPresignedUrlFails() throws Exception {
            when(fileMetaMapper.selectById(1L)).thenReturn(fileMeta);
            when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                    .thenThrow(new RuntimeException("MinIO error"));

            assertThatThrownBy(() -> fileService.getDownloadUrl(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Failed to generate download URL");
        }
    }

    @Nested
    @DisplayName("getFileInfo")
    class GetFileInfoTests {

        @Test
        @DisplayName("should return file metadata when found")
        void shouldReturnFileMetadataWhenFound() {
            FileMeta meta = new FileMeta();
            meta.setFileId(1L);
            meta.setUid(1001L);
            meta.setBucket("files");
            meta.setObjectPath("2024-05/1001/123456.pdf");
            meta.setSize(2048L);
            meta.setExt("pdf");
            meta.setCreatedAt(LocalDateTime.now());
            when(fileMetaMapper.selectById(1L)).thenReturn(meta);

            FileMeta result = fileService.getFileInfo(1L);

            assertThat(result).isNotNull();
            assertThat(result.getFileId()).isEqualTo(1L);
            assertThat(result.getUid()).isEqualTo(1001L);
            assertThat(result.getSize()).isEqualTo(2048L);
        }

        @Test
        @DisplayName("should throw when file metadata not found")
        void shouldThrowWhenFileMetadataNotFound() {
            when(fileMetaMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> fileService.getFileInfo(999L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("File not found");
        }
    }

    @Nested
    @DisplayName("getPresignedUploadUrl")
    class GetPresignedUploadUrlTests {

        @Test
        @DisplayName("should generate presigned upload URL with fileId and path")
        void shouldGeneratePresignedUploadUrl() throws Exception {
            when(snowflakeIdGenerator.nextId()).thenReturn(789L);
            when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                    .thenReturn("http://minio.example.com/files/2024-05/1001/789.pdf?signature=abc");

            Map<String, Object> result = fileService.getPresignedUploadUrl(1001L, "pdf", "file");

            assertThat(result).isNotNull();
            assertThat(result).containsKeys("presignedUrl", "fileId", "bucket", "objectPath");
            assertThat(result.get("fileId")).isEqualTo(789L);
            assertThat(result.get("bucket")).isEqualTo("files");
            assertThat(result.get("presignedUrl").toString()).contains("signature=abc");
        }

        @Test
        @DisplayName("should throw for unsupported extension")
        void shouldThrowForUnsupportedExtension() {
            assertThatThrownBy(() -> fileService.getPresignedUploadUrl(1001L, "exe", "file"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported file type");
        }

        @Test
        @DisplayName("should throw for blank extension")
        void shouldThrowForBlankExtension() {
            assertThatThrownBy(() -> fileService.getPresignedUploadUrl(1001L, "  ", "file"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("File extension is required");
        }

        @Test
        @DisplayName("should throw for null extension")
        void shouldThrowForNullExtension() {
            assertThatThrownBy(() -> fileService.getPresignedUploadUrl(1001L, null, "file"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("File extension is required");
        }

        @Test
        @DisplayName("should throw for null uid without UserContext")
        void shouldThrowForNullUidWithoutUserContext() {
            assertThatThrownBy(() -> fileService.getPresignedUploadUrl(null, "pdf", "file"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("User ID is required");
        }
    }

    @Nested
    @DisplayName("commitUpload")
    class CommitUploadTests {

        @Mock
        private StatObjectResponse statObjectResponse;

        @Test
        @DisplayName("should commit upload and persist metadata")
        void shouldCommitUploadAndPersistMetadata() throws Exception {
            when(statObjectResponse.size()).thenReturn(4096L);
            when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(statObjectResponse);
            when(fileMetaMapper.insert(any(FileMeta.class))).thenReturn(1);

            FileMeta result = fileService.commitUpload(1001L, 1L, "files", "2024-05/1001/1.pdf");

            assertThat(result).isNotNull();
            assertThat(result.getFileId()).isEqualTo(1L);
            assertThat(result.getUid()).isEqualTo(1001L);
            assertThat(result.getBucket()).isEqualTo("files");
            assertThat(result.getObjectPath()).isEqualTo("2024-05/1001/1.pdf");
            assertThat(result.getSize()).isEqualTo(4096L);
            assertThat(result.getExt()).isEqualTo("pdf");
            verify(fileMetaMapper).insert(any(FileMeta.class));
        }

        @Test
        @DisplayName("should throw when fileId is null")
        void shouldThrowWhenFileIdIsNull() {
            assertThatThrownBy(() -> fileService.commitUpload(1001L, null, "files", "path/file.pdf"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fileId is required");
        }

        @Test
        @DisplayName("should throw when bucket is blank")
        void shouldThrowWhenBucketIsBlank() {
            assertThatThrownBy(() -> fileService.commitUpload(1001L, 1L, "  ", "path/file.pdf"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("bucket is required");
        }

        @Test
        @DisplayName("should throw when objectPath is null")
        void shouldThrowWhenObjectPathIsNull() {
            assertThatThrownBy(() -> fileService.commitUpload(1001L, 1L, "files", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("objectPath is required");
        }

        @Test
        @DisplayName("should throw BusinessException when stat fails")
        void shouldThrowWhenStatFails() throws Exception {
            when(minioClient.statObject(any(StatObjectArgs.class)))
                    .thenThrow(new RuntimeException("Object not found"));

            assertThatThrownBy(() -> fileService.commitUpload(1001L, 1L, "files", "missing.pdf"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("File not fully uploaded");
        }
    }
}
