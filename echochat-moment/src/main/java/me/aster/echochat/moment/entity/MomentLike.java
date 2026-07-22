package me.aster.echochat.moment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * moment_like表的实体映射，记录谁点赞了某条动态。
 * @author AsterWinston
 */
@Data
@TableName("moment_like")
public class MomentLike {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 被点赞的动态ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long momentId;

    /** 点赞用户UID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long uid;

    /** 点赞时间戳 */
    private LocalDateTime createdAt;
}
