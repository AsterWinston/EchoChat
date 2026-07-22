package me.aster.echochat.group.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对应group_invite表的实体，表示一个邀请链接。
 * @author AsterWinston
 */
@Data
@TableName("group_invite")
public class GroupInvite {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 目标群组ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long gid;

    /** 唯一邀请码 */
    private String code;

    /** 邀请过期时间戳 */
    private LocalDateTime expireAt;

    /** 邀请是否已被使用（0=未使用，1=已使用） */
    private Integer used;

    private LocalDateTime createdAt;
}
