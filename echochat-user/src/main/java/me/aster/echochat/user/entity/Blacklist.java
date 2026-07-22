package me.aster.echochat.user.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表示用户屏蔽其他用户的黑名单条目实体。
 * @author AsterWinston
 */
@Data
@TableName("blacklist")
public class Blacklist {

    /** 主键。 */
    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.INPUT)
    private Long id;

    /** 创建屏蔽的用户 ID。 */
    private Long uid;

    /** 被屏蔽的用户 ID。 */
    private Long blockedUid;

    /** 屏蔽创建时间。 */
    private LocalDateTime createdAt;
}
