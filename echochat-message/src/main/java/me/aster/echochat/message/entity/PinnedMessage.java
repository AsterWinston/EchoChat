package me.aster.echochat.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * pinned_message表的实体映射，表示会话中已置顶的消息。
 * @author AsterWinston
 */
@Data
@TableName("pinned_message")
public class PinnedMessage {

    @TableId(type = IdType.INPUT)
    private Long id;

    /** 会话类型（如single） */
    private String sessionType;

    /** 对端用户ID或群组ID */
    private String targetId;

    /** 被置顶的消息ID */
    private Long msgId;

    /** 置顶该消息的用户 */
    private Long pinnedBy;

    /** 置顶消息内容的简短摘要 */
    private String contentSummary;

    /** 消息被置顶的时间戳 */
    private LocalDateTime pinnedAt;
}