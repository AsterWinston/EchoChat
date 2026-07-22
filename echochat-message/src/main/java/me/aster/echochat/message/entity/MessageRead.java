package me.aster.echochat.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * message_read表的实体映射，记录用户阅读特定消息的时间。
 * @author AsterWinston
 */
@Data
@TableName("message_read")
public class MessageRead {

    @TableId(type = IdType.INPUT)
    private Long id;

    /** 被阅读的消息 */
    private Long msgId;

    /** 阅读该消息的用户 */
    private Long uid;

    /** 消息被阅读的时间戳 */
    private LocalDateTime readAt;
}