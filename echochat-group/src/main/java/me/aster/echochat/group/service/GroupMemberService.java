package me.aster.echochat.group.service;

import me.aster.echochat.group.entity.GroupMember;
import java.util.List;
import java.util.Map;

/**
 * 群组成员管理操作的服务接口。
 * @author AsterWinston
 */
public interface GroupMemberService {

    /**
     * @param gid 群组ID
     * @return 成员信息map列表（uid, role, muteUntil, joinedAt）
     */
    List<Map<String, Object>> getMembers(Long gid);

    /**
     * @param gid 群组ID
     * @return 成员UID列表
     */
    List<Long> getMemberUids(Long gid);

    /**
     * @param uid  正在加入的用户ID
     * @param code 邀请码
     * @throws me.aster.echochat.common.exception.BusinessException 如果邀请无效或用户已在群中
     */
    void joinByInvite(Long uid, String code);

    /**
     * @param inviterUid 发起邀请的成员UID
     * @param gid        群组ID
     * @param uids       要邀请的UID列表
     */
    void inviteMembers(Long inviterUid, Long gid, List<Long> uids);

    /**
     * @param operatorUid 执行踢出操作的UID
     * @param gid         群组ID
     * @param targetUid   要踢出的UID
     */
    void kickMember(Long operatorUid, Long gid, Long targetUid);

    /**
     * @param uid 退出的UID
     * @param gid 群组ID
     */
    void leaveGroup(Long uid, Long gid);

    /**
     * @param operatorUid 执行角色变更的UID（必须是所有者）
     * @param gid         群组ID
     * @param targetUid   目标成员UID
     * @param role        新角色名称
     */
    void setRole(Long operatorUid, Long gid, Long targetUid, String role);

    /**
     * @param operatorUid 禁言发起者
     * @param gid         群组ID
     * @param targetUid   目标成员UID
     * @param minutes     禁言时长（分钟）
     */
    void muteMember(Long operatorUid, Long gid, Long targetUid, int minutes);

    /**
     * @param operatorUid 解除禁言的发起者
     * @param gid         群组ID
     * @param targetUid   目标成员UID
     */
    void unmuteMember(Long operatorUid, Long gid, Long targetUid);

    /**
     * @param gid 群组ID
     * @param uid 用户ID
     * @return 包含成员状态、角色和禁言状态的map
     */
    Map<String, Object> checkMembership(Long gid, Long uid);

    /**
     * @param gid 群组ID
     * @param uid 用户ID
     * @return {@link GroupMember} 实体，不存在则为null
     */
    GroupMember getMember(Long gid, Long uid);

    /**
     * @param gid 群组ID
     * @return 成员总数
     */
    int getMemberCount(Long gid);
}
