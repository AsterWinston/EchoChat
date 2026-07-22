package me.aster.echochat.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * message_deletion表的实体映射，记录用户级别的消息删除，
 * 使已删除的消息在搜索结果中被过滤。
 * @author AsterWinston
 */
@Data
@TableName("message_deletion")
public class MessageDeletion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long msgId;

    private Long uid;

    private LocalDateTime createdAt;
}