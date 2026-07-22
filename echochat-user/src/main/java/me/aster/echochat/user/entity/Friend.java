package me.aster.echochat.user.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表示单向好友关系的实体。
 * 两条记录（userUid->friendUid 和 friendUid->userUid）组成双向好友关系。
 * @author AsterWinston
 */
@Data
@TableName("friend")
public class Friend {

    /** 主键。 */
    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.INPUT)
    private Long id;

    /** 拥有该好友记录的用户 ID。 */
    private Long userUid;

    /** 好友的用户 ID。 */
    private Long friendUid;

    /** 该好友的自定义分组/类别名称。 */
    private String groupName;

    /** 该好友的备注/别名（可覆盖昵称显示）。 */
    private String memo;

    /** 好友关系创建时间。 */
    private LocalDateTime createdAt;
}
