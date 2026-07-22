package me.aster.echochat.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新好友分组名称和/或备注的 DTO。
 * @author AsterWinston
 */
@Data
public class UpdateFriendRequest {

    /** 自定义分组/类别名称。 */
    @Size(max = 32, message = "must not exceed 32 characters")
    private String groupName;

    /** 好友的个人备注/别名。 */
    @Size(max = 64, message = "must not exceed 64 characters")
    private String memo;
}
