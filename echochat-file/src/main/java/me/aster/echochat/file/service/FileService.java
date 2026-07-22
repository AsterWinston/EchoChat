package me.aster.echochat.file.service;

import me.aster.echochat.file.entity.FileMeta;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

/**
 * 基于MinIO的文件上传和下载操作服务接口。
 * @author AsterWinston
 */
public interface FileService {

    /**
     * 生成预签名PUT URL，用于客户端直传MinIO。
     *
     * @param uid  用户ID（可选，为null时将从UserContext中提取）
     * @param ext  文件扩展名（如 png, jpg, mp4）
     * @param type 存储桶类型（avatar, image, video, file, voice）
     * @return 包含presignedUrl、fileId、bucket和objectPath的map
     */
    Map<String, Object> getPresignedUploadUrl(Long uid, String ext, String type);

    /**
     * 确认上传到MinIO已完成，并记录文件元数据。
     *
     * @param uid        用户ID（可选，为null时将从UserContext中提取）
     * @param fileId     预签名时生成的Snowflake文件ID
     * @param bucket     MinIO存储桶名称
     * @param objectPath 存储桶内的对象路径
     * @return 持久化后的 {@link FileMeta} 记录
     */
    FileMeta commitUpload(Long uid, Long fileId, String bucket, String objectPath);

    /**
     * 生成预签名GET URL，用于下载文件。
     *
     * @param fileId 文件ID
     * @return 预签名下载URL
     */
    String getDownloadUrl(Long fileId);

    /**
     * 根据文件ID获取文件元数据。
     *
     * @param fileId 文件ID
     * @return {@link FileMeta} 记录
     */
    FileMeta getFileInfo(Long fileId);

    /**
     * 通过后端代理上传文件到MinIO，避免跨域问题。
     *
     * @param uid  用户ID（可选，为null时将从UserContext中提取）
     * @param file 上传的文件
     * @param ext  文件扩展名
     * @param type 存储桶类型
     * @return 持久化后的 {@link FileMeta}
     */
    FileMeta upload(Long uid, MultipartFile file, String ext, String type);
}
