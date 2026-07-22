package me.aster.echochat.message.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * message表的实体映射，表示单条聊天消息及其元数据，
 * 包括撤回状态、转发来源和回复引用。
 * @author AsterWinston
 */
@Data
@TableName("message")
public class Message {

    /** Snowflake生成的消息ID，序列化为字符串以避免JS精度丢失 */
    @TableId
    @JsonSerialize(using = ToStringSerializer.class)
    private Long msgId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long fromUid;

    /** 会话类型（如single） */
    private String sessionType;

    /** 接收者用户ID（单聊）或群组ID（群聊），取决于sessionType */
    private String toId;

    /** 消息内容类型（TEXT、IMAGE等） */
    private String msgType;

    private String content;

    /** 消息状态：1=已发送，2=已读 */
    private Integer status;

    /** 消息是否已被撤回，1=已撤回 */
    private Integer isRecalled;

    /** 此消息是否为转发，1=已转发 */
    private Integer isForwarded;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long forwardFromUid;

    /** 被回复的消息ID（如果有） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long replyToMsgId;

    /** 逗号分隔的被@提及的用户ID列表 */
    private String mentionedUids;

    /** 会话内单调递增的序号 */
    private Long seq;

    private LocalDateTime createdAt;
}