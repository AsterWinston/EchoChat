package me.aster.echochat.common.context;

/**
 * 基于ThreadLocal的当前请求已认证用户ID持有者。
 * @author AsterWinston
 */
public class UserContext {

    private UserContext() {}

    /** 存储当前线程作用域内的用户ID */
    private static final ThreadLocal<Long> UID_HOLDER = new ThreadLocal<>();

    /**
     * @param uid 要绑定到当前线程的已认证用户ID
     */
    public static void set(Long uid) {
        UID_HOLDER.set(uid);
    }

    /**
     * @return 当前线程的用户ID，如果未设置则返回null
     */
    public static Long get() {
        return UID_HOLDER.get();
    }

    /** 从当前线程中移除用户ID，防止内存泄漏 */
    public static void clear() {
        UID_HOLDER.remove();
    }
}