package me.aster.echochat.gateway.constant;

import java.util.List;

/**
 * 网关级配置常量，包括免认证路径列表。
 * @author AsterWinston
 */
public final class GatewayConstants {

    private GatewayConstants() {}

    /** 免于JWT认证检查的请求路径。 */
    public static final List<String> EXCLUDE_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh"
    );
}