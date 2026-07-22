package me.aster.echochat.file.service.impl;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.common.context.UserContext;
import me.aster.echochat.common.exception.BusinessException;
import me.aster.echochat.common.result.ResultCode;
import me.aster.echochat.common.util.SnowflakeIdGenerator;
import me.aster.echochat.file.entity.FileMeta;
import me.aster.echochat.file.mapper.FileMetaMapper;
import me.aster.echochat.file.service.FileService;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * {@link FileService} 的MinIO对象存储实现。管理上传预签名、提交确认、下载URL生成、元数据查询、后端代理上传以及图片缩略图生成。
 * @author AsterWinston
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final MinioClient minioClient;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final FileMetaMapper fileMetaMapper;

    /** 用户头像存储桶 */
    private static final String BUCKET_AVATARS = "avatars";
    /** 通用图片存储桶 */
    private static final String BUCKET_IMAGES = "images";
    /** 通用文件存储桶 */
    private static final String BUCKET_FILES = "files";
    /** 视频存储桶 */
    private static final String BUCKET_VIDEOS = "videos";
    /** 语音消息存储桶 */
    private static final String BUCKET_VOICES = "voices";

    /** 上传预签名URL过期时长（30分钟） */
    private static final int UPLOAD_EXPIRY_SECONDS = (int) TimeUnit.MINUTES.toSeconds(30);
    /** 下载预签名URL过期时长（1小时） */
    private static final int DOWNLOAD_EXPIRY_SECONDS = (int) TimeUnit.HOURS.toSeconds(1);

    /** 类型参数到存储桶名称的映射 */
    private static final Map<String, String> BUCKET_MAP = Map.of(
            "avatar", BUCKET_AVATARS,
            "image", BUCKET_IMAGES,
            "file", BUCKET_FILES,
            "video", BUCKET_VIDEOS,
            "voice", BUCKET_VOICES
    );

    /** 每种文件类型允许的扩展名 */
    private static final Map<String, Set<String>> ALLOWED_EXTENSIONS = Map.of(
            "avatar", Set.of("png", "jpg", "jpeg", "gif", "webp"),
            "image", Set.of("png", "jpg", "jpeg", "gif", "webp", "bmp"),
            "video", Set.of("mp4", "avi", "mov", "mkv", "webm"),
            "voice", Set.of("mp3", "wav", "aac", "ogg", "webm"),
            "file", Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "zip", "rar", "7z")
    );

    private static final String DOT = ".";

    /** 所有待初始化的存储桶名称 */
    private static final Set<String> ALL_BUCKETS = Set.of(
            BUCKET_AVATARS, BUCKET_IMAGES, BUCKET_FILES, BUCKET_VIDEOS, BUCKET_VOICES
    );

    /**
     * 在启动时创建所有必需的MinIO存储桶（如果尚不存在）。
     */
    @PostConstruct
    public void initBuckets() {
        for (String bucket : ALL_BUCKETS) {
            try {
                boolean exists = minioClient.bucketExists(
                        BucketExistsArgs.builder().bucket(bucket).build());
                if (!exists) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                    log.info("Created MinIO bucket: {}", bucket);
                }
            } catch (Exception e) {
                log.warn("Failed to initialize MinIO bucket {}: {}", bucket, e.getMessage());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> getPresignedUploadUrl(Long uid, String ext, String type) {
        if (uid == null) {
            Long ctxUid = UserContext.get();
            if (ctxUid != null) {
                uid = ctxUid;
            }
        }
        if (uid == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (ext == null || ext.isBlank()) {
            throw new IllegalArgumentException("File extension is required");
        }
        if (ext.startsWith(DOT)) {
            ext = ext.substring(1);
        }
        ext = ext.toLowerCase();

        Set<String> allowed = ALLOWED_EXTENSIONS.getOrDefault(type, Set.of());
        if (allowed.isEmpty() || !allowed.contains(ext)) {
            throw new IllegalArgumentException("Unsupported file type: " + DOT + ext);
        }

        String bucket = BUCKET_MAP.getOrDefault(type, BUCKET_FILES);

        long fileId = snowflakeIdGenerator.nextId();
        String datePath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String objectPath = String.format("%s/%d/%d.%s", datePath, uid, fileId, ext.toLowerCase());

        try {
            String presignedUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectPath)
                            .expiry(UPLOAD_EXPIRY_SECONDS)
                            .build());

            Map<String, Object> result = new HashMap<>(16);
            result.put("presignedUrl", presignedUrl);
            result.put("fileId", fileId);
            result.put("bucket", bucket);
            result.put("objectPath", objectPath);
            return result;
        } catch (Exception e) {
            log.error("Failed to generate pre-signed upload URL for uid={}, ext={}, type={}", uid, ext, type, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "Failed to generate upload URL");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FileMeta commitUpload(Long uid, Long fileId, String bucket, String objectPath) {
        if (uid == null) {
            Long ctxUid = UserContext.get();
            if (ctxUid != null) {
                uid = ctxUid;
            }
        }
        if (fileId == null) {
            throw new IllegalArgumentException("fileId is required");
        }
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("bucket is required");
        }
        if (objectPath == null || objectPath.isBlank()) {
            throw new IllegalArgumentException("objectPath is required");
        }

        // 获取MinIO对象元数据以验证文件存在并获取大小
        StatObjectResponse stat;
        try {
            stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectPath)
                            .build());
        } catch (Exception e) {
            log.error("Failed to stat MinIO object bucket={}, object={}", bucket, objectPath, e);
            throw new BusinessException(ResultCode.BAD_REQUEST, "File not fully uploaded or not found, upload first");
        }

        // 构建FileMeta记录
        FileMeta meta = new FileMeta();
        meta.setFileId(fileId);
        meta.setUid(uid);
        meta.setBucket(bucket);
        meta.setObjectPath(objectPath);
        meta.setSize(stat.size());
        meta.setCreatedAt(LocalDateTime.now());

        // 从对象路径中提取扩展名（如 "2024-05/1001/123456.png" → "png")
        int dotIndex = objectPath.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < objectPath.length() - 1) {
            meta.setExt(objectPath.substring(dotIndex + 1).toLowerCase());
        }

        // 使用ImageIO检测图片/头像存储桶中的图片尺寸
        if (BUCKET_IMAGES.equals(bucket) || BUCKET_AVATARS.equals(bucket)) {
            processImage(meta);
        }

        fileMetaMapper.insert(meta);
        log.info("File commit successful: fileId={}, bucket={}, size={}", fileId, bucket, meta.getSize());
        return meta;
    }

    /**
     * 从MinIO一次性读取图片，在 {@link FileMeta} 上记录其宽度/高度，
     * 并使用同一解码图片生成缩略图，避免对原始对象进行二次下载。
     *
     * @param meta 需要填充尺寸和thumbnailPath的FileMeta记录
     */
    private void processImage(FileMeta meta) {
        BufferedImage image;
        try (InputStream is = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(meta.getBucket())
                        .object(meta.getObjectPath())
                        .build())) {
            image = ImageIO.read(is);
        } catch (Exception e) {
            log.warn("Failed to read image for fileId={}: {}", meta.getFileId(), e.getMessage());
            return;
        }
        if (image == null) {
            return;
        }
        meta.setWidth(image.getWidth());
        meta.setHeight(image.getHeight());
        generateThumbnail(meta, image);
    }

    /** 生成缩略图的最大宽度/高度 */
    private static final int THUMB_MAX_SIZE = 300;

    /**
     * 为上传到images或avatars存储桶的图片生成缩略图（最大300x300）。缩略图以 {@code _thumb} 后缀存储在MinIO中原图的旁边，
     * 其路径记录在 {@link FileMeta#getThumbnailPath()} 中。
     *
     * @param meta  文件元数据，成功时会在原对象上设置thumbnailPath
     * @param image 已解码的源图片
     */
    private void generateThumbnail(FileMeta meta, BufferedImage image) {
        try {
            BufferedImage thumb = Thumbnails.of(image)
                    .size(THUMB_MAX_SIZE, THUMB_MAX_SIZE)
                    .asBufferedImage();

            String ext = meta.getExt() != null ? meta.getExt() : "jpg";
            String thumbPath = meta.getObjectPath().replaceFirst("\\.[^.]+$", "_thumb." + ext);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(thumb, ext, baos);
            byte[] thumbBytes = baos.toByteArray();

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(meta.getBucket())
                            .object(thumbPath)
                            .stream(new ByteArrayInputStream(thumbBytes), thumbBytes.length, -1)
                            .contentType("image/" + ("jpg".equals(ext) ? "jpeg" : ext))
                            .build());

            meta.setThumbnailPath(thumbPath);
            log.info("Thumbnail generated for fileId={}, path={}", meta.getFileId(), thumbPath);
        } catch (Exception e) {
            log.warn("Failed to generate thumbnail for fileId={}: {}", meta.getFileId(), e.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDownloadUrl(Long fileId) {
        FileMeta meta = fileMetaMapper.selectById(fileId);
        if (meta == null) {
            throw new IllegalArgumentException("File not found: " + fileId);
        }

        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(meta.getBucket())
                            .object(meta.getObjectPath())
                            .expiry(DOWNLOAD_EXPIRY_SECONDS)
                            .build());
        } catch (Exception e) {
            log.error("Failed to generate download URL for fileId={}", fileId, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "Failed to generate download URL");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FileMeta getFileInfo(Long fileId) {
        FileMeta meta = fileMetaMapper.selectById(fileId);
        if (meta == null) {
            throw new IllegalArgumentException("File not found: " + fileId);
        }
        return meta;
    }
    /**
    * {@inheritDoc}
    */
    @Override
    public FileMeta upload(Long uid, MultipartFile file, String ext, String type) {
        if (uid == null) {
            Long ctxUid = UserContext.get();
            if (ctxUid != null) {
                uid = ctxUid;
            }
        }
        if (uid == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (ext.startsWith(DOT)) {
            ext = ext.substring(1);
        }
        ext = ext.toLowerCase();

        Set<String> allowed = ALLOWED_EXTENSIONS.getOrDefault(type, Set.of());
        if (allowed.isEmpty() || !allowed.contains(ext)) {
            throw new IllegalArgumentException("Unsupported file type: " + DOT + ext);
        }

        String bucket = BUCKET_MAP.getOrDefault(type, BUCKET_FILES);
        long fileId = snowflakeIdGenerator.nextId();
        String datePath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String objectPath = String.format("%s/%d/%d.%s", datePath, uid, fileId, ext);

        try (InputStream is = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectPath)
                            .stream(is, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build());
        } catch (Exception e) {
            log.error("Failed to upload file to MinIO: uid={}", uid, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "File upload failed");
        }

        // 构建FileMeta并提交
        FileMeta meta = new FileMeta();
        meta.setFileId(fileId);
        meta.setUid(uid);
        meta.setBucket(bucket);
        meta.setObjectPath(objectPath);
        meta.setSize(file.getSize());
        meta.setExt(ext);
        meta.setCreatedAt(LocalDateTime.now());

        // 检测图片尺寸并为图片生成缩略图
        if (BUCKET_IMAGES.equals(bucket) || BUCKET_AVATARS.equals(bucket)) {
            processImage(meta);
        }

        fileMetaMapper.insert(meta);
        log.info("File upload complete: fileId={}, bucket={}, size={}", fileId, bucket, meta.getSize());
        return meta;
    }
}
