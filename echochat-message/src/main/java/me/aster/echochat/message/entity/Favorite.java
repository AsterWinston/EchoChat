package me.aster.echochat.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * favorite表的实体映射，表示用户收藏（书签）的聊天消息及其摘要快照。
 * @author AsterWinston
 */
@Data
@TableName("favorite")
public class Favorite {

    @TableId(type = IdType.AUTO)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long uid;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long msgId;

    private String msgType;

    private String msgSummary;

    private LocalDateTime collectedAt;
}