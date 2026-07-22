package me.aster.echochat.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 修改成员角色的DTO。
 * @author AsterWinston
 */
@Data
public class SetRoleRequest {

    /** 新角色；所有权转移请使用transfer接口。 */
    @NotBlank(message = "role不能为空")
    @Pattern(regexp = "admin|member", message = "必须是admin或member")
    private String role;
}
