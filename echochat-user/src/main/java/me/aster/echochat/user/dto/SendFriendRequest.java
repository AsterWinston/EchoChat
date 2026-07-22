package me.aster.echochat.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送好友请求的 DTO。
 * @author AsterWinston
 */
@Data
public class SendFriendRequest {

    /** 要添加为好友的用户 UID。 */
    @NotNull(message = "toUid is required")
    private Long toUid;

    /** 可选的申请消息（纯文本）。 */
    @Size(max = 200, message = "must not exceed 200 characters")
    private String message = "";
}
