package me.aster.echochat.message.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 将已有消息转发给好友的DTO。
 * @author AsterWinston
 */
@Data
public class ForwardMessageRequest {

    /** 接收者用户ID。 */
    @NotNull(message = "toUid is required")
    private Long toUid;

    /** 要转发的原始消息ID。 */
    @NotNull(message = "msgId is required")
    private Long msgId;
}