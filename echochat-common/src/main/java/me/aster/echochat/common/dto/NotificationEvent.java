package me.aster.echochat.common.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当业务操作（好友申请、群组邀请、加群审批等）需要产生用户通知时，
 * 发布到 {@code notification-topic} RocketMQ主题的事件。
 * 由echochat-notification消费，在持久化和推送前使用 {@link #eventId} 进行去重。
 * @author AsterWinston
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {

    /** 唯一事件ID（Snowflake），用作消费端幂等键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long eventId;

    /** 接收方用户ID。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long uid;

    /** 通知类型，如 friend_request / friend_accept / group_invite。 */
    private String type;

    /** 通知标题。 */
    private String title;

    /** 通知内容。 */
    private String content;

    /** 关联的业务实体ID（好友申请ID、群组ID等）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long relatedId;
}