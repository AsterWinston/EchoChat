package me.aster.echochat.group.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 禁言群组成员的DTO。
 * @author AsterWinston
 */
@Data
public class MuteMemberRequest {

    /** 禁言时长（分钟），最长30天。 */
    @Min(value = 1, message = "最少1分钟")
    @Max(value = 43200, message = "不能超过43200分钟（30天）")
    private Integer minutes = 10;
}
