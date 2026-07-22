package me.aster.echochat.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * conversation表的实体映射，跟踪每个用户会话的最后消息和未读计数。
 * @author AsterWinston
 */
@Data
@TableName("conversation")
public class Conversation {

    @TableId(type = IdType.INPUT)
    private Long id;

    /** 所属用户ID */
    private Long uid;

    /** 会话类型（如single、group） */
    private String sessionType;

    /** 对端用户ID或群组ID */
    private String targetId;

    /** 该会话中最近一条消息的ID */
    private Long lastMsgId;

    /** 所属用户的未读消息数 */
    private Integer unreadCount;

    /** 会话是否置顶（1=置顶，0=未置顶） */
    private Integer isPinned;

    /** 免打扰标志（1=已静音，0=正常） */
    private Integer dnd;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}