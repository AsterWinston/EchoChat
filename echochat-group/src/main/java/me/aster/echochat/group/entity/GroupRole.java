package me.aster.echochat.group.entity;

/**
 * 表示群组内层次角色的枚举。
 * @author AsterWinston
 */
public enum GroupRole {
    /** 群组所有者，拥有全部权限 */
    OWNER,
    /** 群组管理员 */
    ADMIN,
    /** 普通群组成员 */
    MEMBER;

    /**
     * 将字符串转换为GroupRole，无效输入默认返回MEMBER。
     *
     * @param role 角色名称字符串
     * @return 对应的 {@link GroupRole}
     */
    public static GroupRole from(String role) {
        if (role == null) {
            return MEMBER;
        }
        try {
            return valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MEMBER;
        }
    }

    /** @return 如果此角色拥有管理权限则返回true */
    public boolean canManage() {
        return this == OWNER || this == ADMIN;
    }

}
