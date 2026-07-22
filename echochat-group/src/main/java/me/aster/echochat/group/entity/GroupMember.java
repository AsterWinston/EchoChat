package me.aster.echochat.group.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对应group_member表的实体，表示用户在群组中的成员关系。
 * @author AsterWinston
 */
@Data
@TableName("group_member")
public class GroupMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 群组ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long gid;

    /** 用户ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long uid;

    /** 群组中的角色（owner, admin, member） */
    private String role;

    /** 禁言过期时间，未禁言则为null */
    private LocalDateTime muteUntil;

    private LocalDateTime joinedAt;
}
