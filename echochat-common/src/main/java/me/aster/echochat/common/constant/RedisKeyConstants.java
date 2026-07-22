package me.aster.echochat.common.constant;

/**
 * Redis键名前缀常量定义，统一管理所有服务的缓存键命名规范。
 * @author AsterWinston
 */
public final class RedisKeyConstants {

    private RedisKeyConstants() {}

    /** 访问令牌前缀 */
    public static final String TOKEN_ACCESS_PREFIX = "token:access:";
    /** 刷新令牌前缀 */
    public static final String TOKEN_REFRESH_PREFIX = "token:refresh:";
    /** 令牌黑名单前缀 */
    public static final String TOKEN_BLACKLIST_PREFIX = "token:blacklist:";
    /** 用户在线状态前缀 */
    public static final String USER_ONLINE_PREFIX = "user:online:";
    /** 用户最后在线时间前缀 */
    public static final String USER_LAST_SEEN_PREFIX = "user:last_seen:";
    /** 用户设备前缀 */
    public static final String USER_DEVICES_PREFIX = "user:devices:";
    /** 消息序号前缀 */
    public static final String SEQ_PREFIX = "seq:";
    /** 未读消息前缀 */
    public static final String UNREAD_PREFIX = "unread";
    /** 群成员索引前缀 */
    public static final String GROUP_MEMBER_IDX_PREFIX = "group:member:idx:";
    /** 已读位图前缀 */
    public static final String READ_BITMAP_PREFIX = "read:";
    /** IP级别限流前缀 */
    public static final String RATE_LIMIT_IP_PREFIX = "rate:ip:";
    /** 登录限流前缀 */
    public static final String RATE_LIMIT_LOGIN_PREFIX = "rate:login:";
    /** 注册限流前缀 */
    public static final String RATE_LIMIT_REGISTER_PREFIX = "rate:register:";
    /** 消息发送限流前缀 */
    public static final String RATE_LIMIT_MSG_PREFIX = "rate:msg:";
    /** 消息转发限流前缀 */
    public static final String RATE_LIMIT_FWD_PREFIX = "rate:fwd:";
    /** 慢速模式前缀 */
    public static final String SLOWMODE_PREFIX = "slowmode:";
    /** ES 聊天消息索引前缀 */
    public static final String ES_INDEX_PREFIX = "chat_message";
    /** 群聊序号键后缀 */
    public static final String SEQ_GROUP_SUFFIX = "group:";
    /** 单聊序号键后缀 */
    public static final String SEQ_SINGLE_SUFFIX = "single:";
    /** 注册验证码前缀 */
    public static final String EMAIL_REGISTER_VERIFY_PREFIX = "email:register-verify:";
    /** 注册频率限制前缀 */
    public static final String EMAIL_REGISTER_LIMIT_PREFIX = "email:register-limit:";
    /** 重置密码验证码前缀 */
    public static final String EMAIL_RESET_VERIFY_PREFIX = "email:reset-verify:";
    /** 重置密码频率限制前缀 */
    public static final String EMAIL_RESET_LIMIT_PREFIX = "email:reset-limit:";
    /** 注销验证码前缀 */
    public static final String EMAIL_DELETE_VERIFY_PREFIX = "email:delete-verify:";
}