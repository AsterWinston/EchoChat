package me.aster.echochat.auth.constant;

import java.time.format.DateTimeFormatter;

/**
 * 认证相关常量，包括令牌过期时间和日期格式。
 * @author AsterWinston
 */
public final class AuthConstants {

    private AuthConstants() {}

    /** 访问令牌过期时间，单位为秒（15 分钟）。 */
    public static final long ACCESS_TOKEN_EXPIRATION_SECONDS = 15 * 60;
    /** 刷新令牌过期时间，单位为秒（7 天）。 */
    public static final long REFRESH_TOKEN_EXPIRATION_SECONDS = 7 * 24 * 60 * 60;

    /** 访问令牌过期时间，单位为毫秒（15 分钟）。 */
    public static final long ACCESS_TOKEN_EXPIRATION_MILLIS = 15 * 60 * 1000L;
    /** 刷新令牌过期时间，单位为毫秒（7 天）。 */
    public static final long REFRESH_TOKEN_EXPIRATION_MILLIS = 7 * 24 * 60 * 60 * 1000L;

    /** 刷新令牌类型标识。 */
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    /** 设备登录记录的标准日期时间格式化器。 */
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
}
