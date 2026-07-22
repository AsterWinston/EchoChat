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

    /** 内部路径前缀。 */
    public static final String INTERNAL_PATH_PREFIX = "/internal/";

    /** IPv4映射IPv6地址前缀。 */
    public static final String IPV4_MAPPED_IPV6_PREFIX = "::ffff:";

    /** JWT Bearer前缀。 */
    public static final String BEARER_PREFIX = "Bearer ";

    /** 访问令牌类型。 */
    public static final String TOKEN_TYPE_ACCESS = "access";

    /** 黑名单检查错误标记。 */
    public static final String BLACKLIST_ERROR_TOKEN = "__BLACKLIST_ERROR__";

    /** 登录路径。 */
    public static final String PATH_AUTH_LOGIN = "/api/auth/login";

    /** 注册路径。 */
    public static final String PATH_AUTH_REGISTER = "/api/auth/register";

    /** 消息发送路径。 */
    public static final String PATH_MESSAGE_SEND = "/api/message/send";

    /** 群消息发送路径。 */
    public static final String PATH_MESSAGE_SEND_GROUP = "/api/message/send/group";

    /** 消息转发路径。 */
    public static final String PATH_MESSAGE_FORWARD = "/api/message/forward";
}