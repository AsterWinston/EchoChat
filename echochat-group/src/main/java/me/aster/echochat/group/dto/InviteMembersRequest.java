package me.aster.echochat.group.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 邀请用户加入群组的DTO。
 * @author AsterWinston
 */
@Data
public class InviteMembersRequest {

    /** 要邀请的UID列表。 */
    @NotEmpty(message = "uids不能为空")
    @Size(max = 100, message = "一次最多邀请100个用户")
    private List<Long> uids;
}
