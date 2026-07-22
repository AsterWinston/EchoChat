package me.aster.echochat.notification.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 映射到{@code notification}数据库表的实体类。
 * 存储通知详情，包括类型、标题、内容、已读状态和时间戳。
 * @author AsterWinston
 */
@Data
@TableName("notification")
public class Notification {

    /** 主键，通过雪花算法生成。 */
    @TableId
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 目标用户ID。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long uid;

    /** 通知类型（如"like"、"comment"、"system"）。 */
    private String type;

    /** 简短的通知标题。 */
    private String title;

    /** 详细的通知内容。 */
    private String content;

    /** 关联资源ID（如帖子ID、评论ID），可选。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long relatedId;

    /** 来源事件ID（MQ幂等键）；直接创建的通知为null。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long eventId;

    /** 已读状态：0 = 未读，1 = 已读。 */
    private Integer isRead;

    /** 创建时间戳。 */
    private LocalDateTime createdAt;
}
