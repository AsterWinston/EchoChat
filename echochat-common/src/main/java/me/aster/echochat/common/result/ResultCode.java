package me.aster.echochat.common.result;

import lombok.Getter;

/**
 * 所有服务通用的标准化API结果码枚举。
 * @author AsterWinston
 */
@Getter
public enum ResultCode {

    /** 操作成功 */
    SUCCESS(200, "success"),

    /** 请求参数无效 */
    BAD_REQUEST(400, "Bad request"),
    /** 需要认证 */
    UNAUTHORIZED(401, "Not logged in or token expired"),
    /** 访问被拒绝 */
    FORBIDDEN(403, "No permission"),
    /** 资源不存在 */
    NOT_FOUND(404, "Resource not found"),
    /** HTTP方法不支持 */
    METHOD_NOT_ALLOWED(405, "Method not allowed"),
    /** 资源冲突（重复） */
    CONFLICT(409, "Resource conflict"),
    /** 资源已永久删除 */
    GONE(410, "Resource gone"),
    /** 超出限流 */
    RATE_LIMITED(429, "Too many requests"),

    /** 服务器内部错误 */
    INTERNAL_ERROR(500, "Internal server error"),
    /** 服务暂时不可用 */
    SERVICE_UNAVAILABLE(503, "Service unavailable"),

    /** 用户不存在 */
    USER_NOT_FOUND(1001, "User not found"),
    /** 邮箱已注册 */
    USER_ALREADY_EXISTS(1002, "User already exists"),
    /** 密码不匹配 */
    PASSWORD_ERROR(1003, "Incorrect password"),
    /** JWT已过期 */
    TOKEN_EXPIRED(1004, "Token expired"),
    /** JWT签名无效 */
    TOKEN_INVALID(1005, "Token invalid"),
    /** 设备未连接 */
    DEVICE_OFFLINE(1006, "Device offline"),
    /** 账户已注销 */
    ACCOUNT_DELETED(1007, "Account deleted"),

    /** 消息不存在 */
    MESSAGE_NOT_FOUND(2001, "Message not found"),
    /** 超过撤回时间窗口 */
    MESSAGE_RECALL_TIME_EXCEEDED(2002, "Recall time exceeded"),
    /** 非好友消息限额已用完 */
    NON_FRIEND_MESSAGE_LIMIT(2003, "Non-friend message limit reached"),

    /** 群组不存在 */
    GROUP_NOT_FOUND(3001, "Group not found"),
    /** 群已满员 */
    GROUP_FULL(3002, "Group is full"),
    /** 不是群成员 */
    NOT_GROUP_MEMBER(3003, "Not a group member"),
    /** 群组权限不足 */
    NO_GROUP_PERMISSION(3004, "No group operation permission"),
    /** 群主不能退群，需先转让所有权 */
    GROUP_OWNER_CANNOT_QUIT(3005, "Owner cannot quit, transfer ownership first"),

    /** 动态不存在 */
    MOMENT_NOT_FOUND(4001, "Moment not found"),
    /** 查看者被屏蔽 */
    MOMENT_PERMISSION_DENIED(4002, "No permission to view this moment"),

    /** 通知不存在 */
    NOTIFICATION_NOT_FOUND(5001, "Notification not found"),
    /** 非通知接收者 */
    NOTIFICATION_PERMISSION_DENIED(5002, "No permission for this notification"),
    /** WebSocket推送失败 */
    NOTIFICATION_PUSH_FAILED(5003, "Notification push failed");

    private final int code;

    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

}