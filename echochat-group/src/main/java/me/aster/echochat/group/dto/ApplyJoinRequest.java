package me.aster.echochat.group.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 申请加入群组的DTO。
 * @author AsterWinston
 */
@Data
public class ApplyJoinRequest {

    /** 可选的申请留言，审批者可见。 */
    @Size(max = 200, message = "不能超过200个字符")
    private String message = "";
}
