package me.aster.echochat.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建新群组的DTO。
 * @author AsterWinston
 */
@Data
public class CreateGroupRequest {

    /** 群组显示名称。 */
    @NotBlank(message = "群名不能为空")
    @Size(max = 50, message = "不能超过50个字符")
    private String name;
}
