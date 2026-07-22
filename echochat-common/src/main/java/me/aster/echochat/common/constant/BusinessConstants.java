package me.aster.echochat.common.constant;

import java.time.Duration;

/**
 * 业务常量定义，包含会话类型、申请状态、角色、消息类型、Redis TTL、Feign Map Key 等共享常量。
 * @author AsterWinston
 */
public final class BusinessConstants {

    private BusinessConstants() {}

    /** 会话类型：单聊 */
    public static final String SESSION_TYPE_SINGLE = "single";
    /** 会话类型：群聊 */
    public static final String SESSION_TYPE_GROUP = "group";
    /** 申请状态：待处理 */
    public static final String REQUEST_STATUS_PENDING = "pending";
    /** 申请状态：已接受 */
    public static final String REQUEST_STATUS_ACCEPTED = "accepted";
    /** 申请状态：已拒绝 */
    public static final String REQUEST_STATUS_REJECTED = "rejected";
    /** 申请状态：已过期 */
    public static final String REQUEST_STATUS_EXPIRED = "expired";
    /** 群组角色：群主 */
    public static final String ROLE_OWNER = "owner";
    /** 群组角色：管理员 */
    public static final String ROLE_ADMIN = "admin";
    /** 群组角色：成员 */
    public static final String ROLE_MEMBER = "member";

    /** 消息类型：文本 */
    public static final String MSG_TYPE_TEXT = "TEXT";
    /** 消息类型：系统 */
    public static final String MSG_TYPE_SYSTEM = "SYSTEM";
    /** 消息类型：图片 */
    public static final String MSG_TYPE_IMAGE = "IMAGE";
    /** 消息类型：文件 */
    public static final String MSG_TYPE_FILE = "FILE";
    /** 消息类型：语音 */
    public static final String MSG_TYPE_VOICE = "VOICE";
    /** 消息类型：视频 */
    public static final String MSG_TYPE_VIDEO = "VIDEO";

    /** 群成员查询 Map Key：是否为成员 */
    public static final String MEMBERSHIP_KEY_MEMBER = "member";
    /** 群成员查询 Map Key：角色 */
    public static final String MEMBERSHIP_KEY_ROLE = "role";
    /** 群成员查询 Map Key：是否被禁言 */
    public static final String MEMBERSHIP_KEY_MUTED = "muted";
    /** 群成员查询 Map Key：全员禁言 */
    public static final String MEMBERSHIP_KEY_MUTE_ALL = "muteAll";
    /** 群成员查询 Map Key：慢速模式间隔 */
    public static final String MEMBERSHIP_KEY_SLOW_MODE_INTERVAL = "slowModeInterval";
    /** 好友关系查询 Map Key */
    public static final String FRIENDSHIP_KEY_FRIENDS = "friends";
    /** 黑名单查询 Map Key */
    public static final String BLACKLIST_KEY_BLOCKED = "blocked";
    /** 成员数量查询 Map Key */
    public static final String MEMBER_COUNT_KEY = "count";

    /** 群组消息广播阈值（成员数超过此值时切换为拉取模式） */
    public static final int GROUP_FANOUT_THRESHOLD = 500;
    /** 消息撤回超时时间（分钟） */
    public static final int MESSAGE_RECALL_TIMEOUT_MINUTES = 2;
    /** 好友申请过期天数 */
    public static final int FRIEND_REQUEST_EXPIRY_DAYS = 3;
    /** 动态归档天数 */
    public static final int FEED_ARCHIVE_DAYS = 90;
    /** Socket 连接全连接队列大小 */
    public static final int SO_BACKLOG = 128;
    /** HTTP 请求体最大长度（字节） */
    public static final int MAX_HTTP_CONTENT_LENGTH = 65536;
    /** Bearer 前缀长度 */
    public static final int BEARER_PREFIX_LENGTH = 7;
    /** 消息分页默认最大值 */
    public static final int MESSAGE_PAGE_LIMIT = 200;
    /** 群名称最大长度 */
    public static final int GROUP_NAME_MAX_LENGTH = 128;
    /** ES HTTP 连接超时（毫秒） */
    public static final int ES_CONNECT_TIMEOUT = 5000;
    /** ES HTTP 读取超时（毫秒） */
    public static final int ES_READ_TIMEOUT = 10000;

    /** 未知 IP 兜底值 */
    public static final String UNKNOWN_IP = "unknown";
    /** 未知平台兜底值 */
    public static final String UNKNOWN_PLATFORM = "unknown";
    /** 默认群组名称 */
    public static final String DEFAULT_GROUP_NAME = "Group Chat";
    /** 用户ID请求头 */
    public static final String USER_ID_HEADER = "X-User-Id";

    /** Redis 默认键过期时间（30天） */
    public static final Duration REDIS_DEFAULT_TTL = Duration.ofDays(30);
    /** Redis 一周过期时间 */
    public static final Duration REDIS_WEEK_TTL = Duration.ofDays(7);
    /** Redis 一天过期时间 */
    public static final Duration REDIS_DAY_TTL = Duration.ofDays(1);
    /** Redis 操作超时时间（1秒） */
    public static final Duration REDIS_TIMEOUT_1S = Duration.ofSeconds(1);
}
