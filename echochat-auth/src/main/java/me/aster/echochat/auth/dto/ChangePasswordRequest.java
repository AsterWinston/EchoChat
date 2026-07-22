package me.aster.echochat.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 密码修改请求的 DTO。
 * @author AsterWinston
 */
@Data
public class ChangePasswordRequest {

    /** 当前密码，用于验证。 */
    @NotBlank(message = "oldPassword is required")
    @Size(max = 64, message = "must not exceed 64 characters")
    private String oldPassword;

    /** 要设置的新密码。 */
    @NotBlank(message = "newPassword is required")
    @Size(min = 8, max = 64, message = "must be 8-64 characters")
    private String newPassword;
}
