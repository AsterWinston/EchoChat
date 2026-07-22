package me.aster.echochat.moment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * moment_privacy表的实体映射，记录哪些用户被禁止查看某条"restricted"可见性的动态。
 * @author AsterWinston
 */
@Data
@TableName("moment_privacy")
public class MomentPrivacy {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 受限的动态ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long momentId;

    /** 被禁止查看该动态的用户UID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long blockUid;
}
