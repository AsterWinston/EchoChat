package me.aster.echochat.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * offline_message表的实体映射，存储不在线用户的消息。
 * @author AsterWinston
 */
@Data
@TableName("offline_message")
public class OfflineMessage {

    @TableId(type = IdType.INPUT)
    private Long id;

    /** 离线的接收者用户 */
    private Long uid;

    /** 对原始消息的引用 */
    private Long msgId;

    /** 消息的序号 */
    private Long seq;

    private LocalDateTime createdAt;
}