package me.aster.echochat.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import me.aster.echochat.common.constant.BusinessConstants;

/**
 * 发送单聊消息的DTO。
 * @author AsterWinston
 */
@Data
public class SendMessageRequest {

    /** 接收者用户ID。 */
    @NotNull(message = "toUid is required")
    private Long toUid;

    /** 消息内容类型；SYSTEM类型消息不能通过公开API发送。 */
    @Pattern(regexp = "TEXT|IMAGE|FILE|VOICE|VIDEO", message = "must be one of TEXT/IMAGE/FILE/VOICE/VIDEO")
    private String msgType = BusinessConstants.MSG_TYPE_TEXT;

    /** 消息正文（纯文本或类型特定的JSON）。 */
    @NotBlank(message = "content is required")
    @Size(max = 20000, message = "must not exceed 20000 characters")
    private String content;
}