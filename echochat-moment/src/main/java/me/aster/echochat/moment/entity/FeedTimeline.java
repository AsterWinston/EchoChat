package me.aster.echochat.moment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * feed_timeline表的实体映射，代表用户的时间线收件箱条目。
 * 每一行是通过写扩散模式推送到好友时间线中的一条动态副本。
 * @author AsterWinston
 */
@Data
@TableName("feed_timeline")
public class FeedTimeline {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 时间线收件箱的所有者UID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerUid;

    /** 动态ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long momentId;

    /** 动态作者UID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long authorUid;

    /** 动态发布时间（镜像moment.created_at） */
    private LocalDateTime createdAt;
}
