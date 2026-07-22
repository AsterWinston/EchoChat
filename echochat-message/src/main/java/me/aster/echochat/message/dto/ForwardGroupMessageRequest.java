package me.aster.echochat.message.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 将已有消息转发到群组的DTO。
 * @author AsterWinston
 */
@Data
public class ForwardGroupMessageRequest {

    /** 目标群组ID。 */
    @NotNull(message = "gid is required")
    private Long gid;

    /** 要转发的原始消息ID。 */
    @NotNull(message = "msgId is required")
    private Long msgId;
}