package me.aster.echochat.message.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 在会话中置顶消息的DTO。
 * @author AsterWinston
 */
@Data
public class PinMessageRequest {

    /** 对话对端UID（单聊），用于权限解析。 */
    @NotNull(message = "targetUid is required")
    private Long targetUid;

    /** 置顶横幅中显示的简短摘要。 */
    @Size(max = 256, message = "must not exceed 256 characters")
    private String contentSummary = "";
}