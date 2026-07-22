package me.aster.echochat.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 在单聊中回复（引用）已有消息的DTO。
 * @author AsterWinston
 */
@Data
public class ReplyMessageRequest {

    /** 接收者用户ID。 */
    @NotNull(message = "toUid is required")
    private Long toUid;

    /** 被引用的消息ID。 */
    @NotNull(message = "replyToMsgId is required")
    private Long replyToMsgId;

    /** 回复文本内容。 */
    @NotBlank(message = "content is required")
    @Size(max = 20000, message = "must not exceed 20000 characters")
    private String content;
}