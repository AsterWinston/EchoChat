package me.aster.echochat.group.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对应group_info表的实体，表示一个聊天群组。
 * @author AsterWinston
 */
@Data
@TableName("group_info")
public class GroupInfo {

    /** 主键，通过Snowflake生成 */
    @TableId(type = IdType.INPUT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long gid;

    /** 群组显示名称 */
    private String name;

    /** 群组头像URL */
    private String avatar;

    /** 群组所有者的UID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerUid;

    /** 群组公告内容 */
    private String announcement;

    /** 慢速模式间隔（秒），0表示禁用 */
    private Integer slowModeInterval;

    /** 全员禁言标志，0=关闭，1=开启 */
    private Integer muteAll;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
