package me.aster.echochat.common.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 映射到 {@code user} 表的用户实体。
 * @author AsterWinston
 */
@Data
@TableName("user")
public class User {

    /** 主键，序列化为字符串以避免JavaScript精度丢失 */
    @TableId
    @JsonSerialize(using = ToStringSerializer.class)
    private Long uid;

    /** 显示名称 */
    private String nickname;

    /** 邮箱地址 */
    private String email;

    /** 加密后的密码 */
    private String password;

    /** 头像URL */
    private String avatar;

    /** 个性签名 / 状态文本 */
    private String signature;

    /** 0=未知，1=男，2=女 */
    private Integer gender;

    /** 用户年龄 */
    private Integer age;

    /** 账户状态 */
    private Integer status;

    /** 最近在线时间戳 */
    private LocalDateTime lastSeen;

    /** 账户创建时间 */
    private LocalDateTime createdAt;

    /** 最近更新时间 */
    private LocalDateTime updatedAt;

    /** 逻辑删除标记：0=活跃，1=已删除 */
    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;
}