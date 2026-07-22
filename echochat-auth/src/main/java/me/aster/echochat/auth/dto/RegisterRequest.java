package me.aster.echochat.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求的 DTO。
 * @author AsterWinston
 */
@Data
public class RegisterRequest {

    /** 用户邮箱地址。 */
    @NotBlank(message = "email is required")
    @Email(message = "must be a valid email")
    @Size(max = 128, message = "must not exceed 128 characters")
    private String email;

    /** 账户密码。 */
    @NotBlank(message = "password is required")
    @Size(min = 8, max = 64, message = "must be 8-64 characters")
    private String password;

    /** 显示昵称。 */
    @Size(max = 32, message = "must not exceed 32 characters")
    private String nickname;

    /** 客户端设备 ID。 */
    @Size(max = 64, message = "must not exceed 64 characters")
    private String deviceId;

    /** 客户端平台名称。 */
    @Size(max = 32, message = "must not exceed 32 characters")
    private String platform;
}
