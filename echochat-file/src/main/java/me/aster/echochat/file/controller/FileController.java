package me.aster.echochat.file.controller;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import me.aster.echochat.common.constant.BusinessConstants;
import me.aster.echochat.common.result.Result;
import me.aster.echochat.file.entity.FileMeta;
import me.aster.echochat.file.service.FileService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 文件操作REST控制器，提供后端代理上传、预签名上传URL生成、上传确认提交、文件下载代理、下载URL获取和文件元数据查询功能。
 * @author AsterWinston
 */
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final MinioClient minioClient;

    /**
     * 通过后端代理上传文件，避免跨域问题。
     *
     * @param uid  用户ID（可选）
     * @param file 上传的multipart文件
     * @param ext  文件扩展名
     * @param type 存储桶类型（avatar, image, video, file, voice）
     * @return 持久化后的文件元数据
     */
    @PostMapping("/upload")
    public Result<FileMeta> upload(
            @RequestHeader(value = BusinessConstants.USER_ID_HEADER, required = false) Long uid,
            @RequestParam("file") MultipartFile file,
            @RequestParam String ext,
            @RequestParam String type) {
        return Result.ok(fileService.upload(uid, file, ext, type));
    }

    /**
     * 生成预签名上传URL，允许客户端直传文件到MinIO。
     *
     * @param uid  用户ID（可选，未提供时从上下文提取）
     * @param ext  文件扩展名（如 png, jpg, mp4）
     * @param type 存储桶类型（avatar, image, video, file, voice）
     * @return 包含presignedUrl、fileId、bucket和objectPath的map
     */
    @PostMapping("/presign-url")
    public Result<Map<String, Object>> presignUploadUrl(
            @RequestHeader(value = BusinessConstants.USER_ID_HEADER, required = false) Long uid,
            @RequestParam String ext,
            @RequestParam String type) {
        return Result.ok(fileService.getPresignedUploadUrl(uid, ext, type));
    }

    /**
     * 确认客户端上传已完成，并持久化文件元数据。
     *
     * @param uid  用户ID（可选，未提供时从上下文提取）
     * @param body 包含fileId、bucket和objectPath的请求体
     * @return 持久化后的文件元数据
     */
    @PostMapping("/commit")
    public Result<FileMeta> commitUpload(
            @RequestHeader(value = BusinessConstants.USER_ID_HEADER, required = false) Long uid,
            @RequestBody Map<String, Object> body) {
        Object fileIdObj = body.get("fileId");
        Long fileId = fileIdObj != null ? (fileIdObj instanceof Number ? ((Number) fileIdObj).longValue() : Long.parseLong(fileIdObj.toString())) : null;
        String bucket = (String) body.get("bucket");
        String objectPath = (String) body.get("objectPath");
        return Result.ok(fileService.commitUpload(uid, fileId, bucket, objectPath));
    }

    /**
     * 为指定文件生成预签名下载URL。
     *
     * @param fileId 文件ID
     * @return 预签名下载URL
     */
    @GetMapping("/download/{fileId}")
    public Result<String> getDownloadUrl(@PathVariable Long fileId) {
        return Result.ok(fileService.getDownloadUrl(fileId));
    }

    /**
     * 根据文件ID获取文件元数据。
     *
     * @param fileId 文件ID
     * @return 文件元数据记录
     */
    @GetMapping("/info/{fileId}")
    public Result<FileMeta> getFileInfo(@PathVariable Long fileId) {
        return Result.ok(fileService.getFileInfo(fileId));
    }

    /**
     * 将文件内容从MinIO代理到客户端，以二进制流方式返回。
     * 直接使用MinioClient以避免预签名URL的原始HTTP连接问题。
     *
     * @param fileId 文件ID
     * @return 包含文件二进制流内容的ResponseEntity
     */
    @GetMapping("/proxy/{fileId}")
    public ResponseEntity<StreamingResponseBody> proxyFile(@PathVariable Long fileId) {
        FileMeta meta = fileService.getFileInfo(fileId);
        String contentType = resolveContentType(meta);
        String fileName = meta.getOriginalName() != null ? meta.getOriginalName() : "file";
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

        StreamingResponseBody body = out -> {
            try (InputStream in = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(meta.getBucket())
                            .object(meta.getObjectPath())
                            .build())) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to stream file from MinIO", e);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName)
                .body(body);
    }

    private String resolveContentType(FileMeta meta) {
        if (meta.getExt() == null) {
            return "application/octet-stream";
        }
        return switch (meta.getExt().toLowerCase()) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            case "svg" -> "image/svg+xml";
            case "mp4" -> "video/mp4";
            case "webm" -> "video/webm";
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "ogg" -> "audio/ogg";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }
}
