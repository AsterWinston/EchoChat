package me.aster.echochat.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 返回给客户端的认证令牌响应的 DTO。
 * @author AsterWinston
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {

    /** JWT 访问令牌。 */
    private String accessToken;

    /** 用于刷新访问令牌的 JWT 刷新令牌。 */
    private String refreshToken;

    /** 访问令牌的过期时间，单位为秒。 */
    private Long expiresIn;

    /** 已认证的用户 ID。 */
    private Long uid;

    /** 已认证的用户昵称。 */
    private String nickname;
}
