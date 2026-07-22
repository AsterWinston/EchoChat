package me.aster.echochat.common.security;

/**
 * 服务间（内部）认证的共享常量。
 * @author AsterWinston
 */
public final class InternalAuthConstants {

    /** 在调用 /internal/** 端点的Feign请求中携带内部服务令牌的HTTP头。 */
    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    /** 保存共享内部令牌的配置属性。 */
    public static final String INTERNAL_TOKEN_PROPERTY = "security.internal-token";

    private InternalAuthConstants() {
    }
}