package me.aster.echochat.group.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 创建群组邀请链接的DTO。
 * @author AsterWinston
 */
@Data
public class CreateInviteRequest {

    /** 邀请有效时长（小时），最长72小时（超过72小时将被重置为24）。 */
    @Min(value = 1, message = "最少1小时")
    @Max(value = 720, message = "不能超过720小时（30天）")
    private Integer expireHours = 24;
}
