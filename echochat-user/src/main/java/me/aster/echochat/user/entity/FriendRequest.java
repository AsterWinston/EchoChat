package me.aster.echochat.user.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表示一个用户向另一个用户发送的好友请求实体。
 * @author AsterWinston
 */
@Data
@TableName("friend_request")
public class FriendRequest {

    /** 主键。 */
    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.INPUT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 发送方用户 ID。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fromUid;

    /** 接收方用户 ID。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long toUid;

    /** 附加在请求中的可选消息。 */
    private String message;

    /** 当前状态：pending（待处理）、accepted（已接受）、rejected（已拒绝）、expired（已过期）。 */
    private String status;

    /** 待处理请求的过期时间戳。 */
    private LocalDateTime expireAt;

    /** 请求创建时间。 */
    private LocalDateTime createdAt;

    /** 请求被接受或拒绝的时间。 */
    private LocalDateTime handledAt;
}
