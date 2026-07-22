package me.aster.echochat.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求的 DTO。
 * @author AsterWinston
 */
@Data
public class LoginRequest {

    /** 用户账户标识（UID 或邮箱）。 */
    @NotBlank(message = "account is required")
    @Size(max = 128, message = "must not exceed 128 characters")
    private String account;

    /** 用户密码。 */
    @NotBlank(message = "password is required")
    @Size(max = 64, message = "must not exceed 64 characters")
    private String password;

    /** 客户端设备 ID，用于多设备管理。 */
    @Size(max = 64, message = "must not exceed 64 characters")
    private String deviceId;

    /** 客户端平台名称。 */
    @Size(max = 32, message = "must not exceed 32 characters")
    private String platform;
}
