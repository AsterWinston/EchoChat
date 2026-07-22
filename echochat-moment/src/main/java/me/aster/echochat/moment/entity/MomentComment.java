package me.aster.echochat.moment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * moment_comment表的实体映射，存储动态的评论信息，通过replyToUid引用被回复用户（顶级评论时为null）。
 * @author AsterWinston
 */
@Data
@TableName("moment_comment")
public class MomentComment {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 被评论的动态ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long momentId;

    /** 评论者UID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long uid;

    /** 被回复用户UID，顶级评论时为null */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long replyToUid;

    /** 评论文本内容 */
    private String content;

    /** 评论时间戳 */
    private LocalDateTime createdAt;
}
