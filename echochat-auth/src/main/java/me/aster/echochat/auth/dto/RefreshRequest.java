package me.aster.echochat.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 令牌刷新请求的 DTO。
 * @author AsterWinston
 */
@Data
public class RefreshRequest {

    /** 登录时颁发的刷新令牌。 */
    @NotBlank(message = "refreshToken is required")
    private String refreshToken;
}
