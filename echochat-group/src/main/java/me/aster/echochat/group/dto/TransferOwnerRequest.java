package me.aster.echochat.group.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 转让群组所有权的DTO。
 * @author AsterWinston
 */
@Data
public class TransferOwnerRequest {

    /** 成为新所有者的成员UID。 */
    @NotNull(message = "newOwnerUid不能为空")
    private Long newOwnerUid;
}
