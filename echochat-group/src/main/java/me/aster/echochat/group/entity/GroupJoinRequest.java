package me.aster.echochat.group.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对应group_join_request表的实体，表示用户加入群组的请求及其状态和处理详情。
 * @author AsterWinston
 */
@Data
@TableName("group_join_request")
public class GroupJoinRequest {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long gid;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long uid;

    private String status;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long processedBy;

    private String message;

    private LocalDateTime createdAt;

    private LocalDateTime processedAt;
}
