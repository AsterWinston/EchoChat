package me.aster.echochat.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 对应file_meta表的实体，存储每个上传文件的元数据。
 * @author AsterWinston
 */
@Data
@TableName("file_meta")
public class FileMeta {

    /** Snowflake生成的文件ID（主键，手动赋值） */
    @TableId(type = IdType.INPUT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileId;

    /** 文件所有者用户ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long uid;

    /** MinIO存储桶名称（如 avatars、images、files、videos、voices） */
    private String bucket;

    /** 存储桶内的对象路径（如 2024-05/1001/123456.png） */
    private String objectPath;

    /** 用户上传时的原始文件名 */
    private String originalName;

    /** 文件扩展名，不含点号（如 png, jpg） */
    private String ext;

    /** 文件大小（字节） */
    private Long size;

    /** 图片/视频宽度（像素），非视觉类文件为null */
    private Integer width;

    /** 图片/视频高度（像素），非视觉类文件为null */
    private Integer height;

    /** 音频/视频时长（秒），非音视频文件为null */
    private Integer duration;

    /** 生成缩略图的对象路径，无缩略图则为null */
    private String thumbnailPath;

    /** 文件记录创建时间戳 */
    private LocalDateTime createdAt;
}
