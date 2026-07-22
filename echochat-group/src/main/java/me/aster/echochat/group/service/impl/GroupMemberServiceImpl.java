package me.aster.echochat.group.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.common.constant.BusinessConstants;
import me.aster.echochat.common.constant.RedisKeyConstants;
import me.aster.echochat.common.exception.BusinessException;
import me.aster.echochat.common.result.ResultCode;
import me.aster.echochat.group.client.MessageFeignClient;
import me.aster.echochat.common.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import me.aster.echochat.group.client.UserFeignClient;
import me.aster.echochat.group.entity.GroupInfo;
import me.aster.echochat.group.entity.GroupInvite;
import me.aster.echochat.group.entity.GroupMember;
import me.aster.echochat.group.entity.GroupRole;
import me.aster.echochat.group.mapper.GroupInfoMapper;
import me.aster.echochat.group.mapper.GroupInviteMapper;
import me.aster.echochat.group.mapper.GroupMemberMapper;
import me.aster.echochat.group.mq.NotificationEventPublisher;
import me.aster.echochat.group.service.GroupMemberService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@link GroupMemberService} 的实现，处理成员关系、角色管理、禁言、踢人和邀请加入。
 * @author AsterWinston
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupMemberServiceImpl implements GroupMemberService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GroupMemberMapper groupMemberMapper;
    private final GroupInfoMapper groupInfoMapper;
    private final GroupInviteMapper groupInviteMapper;
    private final UserFeignClient userFeignClient;
    private final NotificationEventPublisher notificationEventPublisher;
    private final MessageFeignClient messageFeignClient;
    private final StringRedisTemplate redisTemplate;

    /**
     * @param gid 群组ID
     * @return 成员详情列表（uid, role, muteUntil, joinedAt）
     */
    @Override
    public List<Map<String, Object>> getMembers(Long gid) {
        checkGroupExists(gid);
        List<GroupMember> members = groupMemberMapper.findByGid(gid);
        List<Map<String, Object>> result = new ArrayList<>();
        for (GroupMember m : members) {
            Map<String, Object> item = new LinkedHashMap<>(16);
            item.put("uid", String.valueOf(m.getUid()));
            item.put("role", m.getRole());
            item.put("muteUntil", m.getMuteUntil());
            item.put("joinedAt", m.getJoinedAt());
            result.add(item);
        }
        return result;
    }

    /**
     * @param gid 群组ID
     * @return 群组中成员UID的扁平列表
     */
    @Override
    public List<Long> getMemberUids(Long gid) {
        checkGroupExists(gid);
        return groupMemberMapper.findUidsByGid(gid);
    }

    /**
     * 通过一次性邀请码加入群组。验证邀请码是否存在、是否已使用、是否过期以及是否重复加入。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void joinByInvite(Long uid, String code) {
        GroupInvite invite = groupInviteMapper.findByCode(code);
        if (invite == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "Invite link not found");
        }
        if (invite.getUsed() != null && invite.getUsed() == 1) {
            throw new BusinessException(ResultCode.GONE.getCode(), "Invite link already used");
        }
        if (invite.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.GONE.getCode(), "Invite link expired");
        }

        Long gid = invite.getGid();
        checkGroupExists(gid);

        GroupMember existing = groupMemberMapper.findByGidAndUid(gid, uid);
        if (existing != null) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "Already a member of this group");
        }

        GroupMember member = new GroupMember();
        member.setGid(gid);
        member.setUid(uid);
        member.setRole(GroupRole.MEMBER.name().toLowerCase());
        member.setJoinedAt(LocalDateTime.now());
        groupMemberMapper.insert(member);

        assignMemberIndex(gid, uid);
        invite.setUsed(1);
        groupInviteMapper.updateById(invite);

        sendSystemMessage(gid, uid, "join");

        log.info("User joined group via invite: uid={}, gid={}", uid, gid);
    }

    /**
     * 邀请多个用户加入群组。要求邀请者必须是所有者或管理员、是成员且未被禁言。对每个被邀请的UID通过用户服务进行验证；已有成员会被静默跳过。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void inviteMembers(Long inviterUid, Long gid, List<Long> uids) {
        GroupMember inviter = checkMember(gid, inviterUid);
        checkNotMuted(inviter);
        checkCanManage(inviter);

        for (Long uid : uids) {
            validateUidExists(uid);
            GroupMember existing = groupMemberMapper.findByGidAndUid(gid, uid);
            if (existing != null) {
                continue;
            }
            GroupMember member = new GroupMember();
            member.setGid(gid);
            member.setUid(uid);
            member.setRole(GroupRole.MEMBER.name().toLowerCase());
            member.setJoinedAt(LocalDateTime.now());
            groupMemberMapper.insert(member);
            assignMemberIndex(gid, uid);

            notificationEventPublisher.publishAfterCommit(uid, "group_invite",
                    "Group Invitation", "You are invited to join a group", gid);
        }
        log.info("Members invited: gid={}, by={}, count={}", gid, inviterUid, uids.size());
    }

    /**
     * 将成员踢出群组。仅所有者/管理员可踢人；所有者不可被踢；管理员不能踢出其他管理员。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void kickMember(Long operatorUid, Long gid, Long targetUid) {
        GroupMember operator = checkMember(gid, operatorUid);
        checkCanManage(operator);

        GroupMember target = checkMember(gid, targetUid);

        if (GroupRole.from(target.getRole()) == GroupRole.OWNER) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Cannot kick the owner");
        }
        if (GroupRole.from(operator.getRole()) == GroupRole.ADMIN
                && GroupRole.from(target.getRole()) == GroupRole.ADMIN) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Admins cannot kick other admins");
        }

        groupMemberMapper.deleteById(target.getId());
        removeMemberIndex(gid, targetUid);
        sendSystemMessage(gid, targetUid, "kick");
        log.info("Member kicked: gid={}, operator={}, target={}", gid, operatorUid, targetUid);
    }

    /** 所有者不能直接退出；请先转让所有权。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void leaveGroup(Long uid, Long gid) {
        GroupMember member = checkMember(gid, uid);

        if (GroupRole.from(member.getRole()) == GroupRole.OWNER) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Owner cannot leave directly, transfer ownership first");
        }

        groupMemberMapper.deleteById(member.getId());
        removeMemberIndex(gid, uid);
        sendSystemMessage(gid, uid, "leave");
        log.info("Member left group: gid={}, uid={}", gid, uid);
    }

    /**
     * 设置成员角色（admin/member）。仅所有者可执行此操作。不能设置为owner（请使用transferOwner）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setRole(Long operatorUid, Long gid, Long targetUid, String role) {
        GroupMember operator = checkMember(gid, operatorUid);
        if (GroupRole.from(operator.getRole()) != GroupRole.OWNER) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Only owner can set roles");
        }

        GroupMember target = checkMember(gid, targetUid);

        GroupRole newRole = GroupRole.from(role);
        if (newRole == GroupRole.OWNER) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "Please use transfer ownership function");
        }

        target.setRole(newRole.name().toLowerCase());
        groupMemberMapper.updateById(target);
        log.info("Member role updated: gid={}, target={}, role={}", gid, targetUid, newRole);
    }

    /**
     * 对成员禁言指定分钟数。仅所有者/管理员可禁言。所有者和同级管理员不可被禁言。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void muteMember(Long operatorUid, Long gid, Long targetUid, int minutes) {
        GroupMember operator = checkMember(gid, operatorUid);
        checkCanManage(operator);

        GroupMember target = checkMember(gid, targetUid);

        if (GroupRole.from(target.getRole()) == GroupRole.OWNER) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Cannot mute the owner");
        }
        if (GroupRole.from(operator.getRole()) == GroupRole.ADMIN
                && GroupRole.from(target.getRole()) == GroupRole.ADMIN) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Admins cannot mute other admins");
        }

        target.setMuteUntil(LocalDateTime.now().plusMinutes(minutes));
        groupMemberMapper.updateById(target);
        log.info("Member muted: gid={}, target={}, minutes={}", gid, targetUid, minutes);
    }

    /** 解除成员禁言，将muteUntil设置为null。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unmuteMember(Long operatorUid, Long gid, Long targetUid) {
        GroupMember operator = checkMember(gid, operatorUid);
        checkCanManage(operator);

        GroupMember target = checkMember(gid, targetUid);
        target.setMuteUntil(null);
        groupMemberMapper.updateById(target);
        log.info("Member unmuted: gid={}, target={}", gid, targetUid);
    }

    /**
     * 检查用户在群组中的成员状态，返回包括角色、禁言状态和慢速模式间隔在内的成员详情。
     *
     * @param gid 群组ID
     * @param uid 用户ID
     * @return 包含以下键的map：member (布尔值), role, muted, slowModeInterval, muteAll
     */
    @Override
    public Map<String, Object> checkMembership(Long gid, Long uid) {
        GroupMember member = groupMemberMapper.findByGidAndUid(gid, uid);
        Map<String, Object> result = new LinkedHashMap<>(16);
        if (member == null) {
            result.put(BusinessConstants.MEMBERSHIP_KEY_MEMBER, false);
            return result;
        }
        result.put(BusinessConstants.MEMBERSHIP_KEY_MEMBER, true);
        result.put(BusinessConstants.MEMBERSHIP_KEY_ROLE, member.getRole());
        result.put(BusinessConstants.MEMBERSHIP_KEY_MUTED, member.getMuteUntil() != null
                && member.getMuteUntil().isAfter(LocalDateTime.now()));
        GroupInfo group = groupInfoMapper.selectById(gid);
        result.put(BusinessConstants.MEMBERSHIP_KEY_SLOW_MODE_INTERVAL, group != null ? group.getSlowModeInterval() : 0);
        result.put(BusinessConstants.MEMBERSHIP_KEY_MUTE_ALL, group != null && group.getMuteAll() != null && group.getMuteAll() == 1);
        return result;
    }

    /**
     * 根据群组ID和用户ID获取单个成员记录。
     *
     * @param gid 群组ID
     * @param uid 用户ID
     * @return 群组成员实体，非成员则返回null
     */
    @Override
    public GroupMember getMember(Long gid, Long uid) {
        return groupMemberMapper.findByGidAndUid(gid, uid);
    }

    /**
     * 返回群组中的成员总数。
     *
     * @param gid 群组ID
     * @return 成员数量
     */
    @Override
    public int getMemberCount(Long gid) {
        return groupMemberMapper.countByGid(gid);
    }

    /** @throws BusinessException 如果群组不存在，抛出NOT_FOUND错误 */
    private void checkGroupExists(Long gid) {
        if (groupInfoMapper.selectById(gid) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "Group not found");
        }
    }

    /** @return GroupMember实体，如果不是成员则抛出FORBIDDEN */
    private GroupMember checkMember(Long gid, Long uid) {
        GroupMember member = groupMemberMapper.findByGidAndUid(gid, uid);
        if (member == null) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Not a member of this group");
        }
        return member;
    }

    /** @throws BusinessException 如果成员缺少owner/admin角色 */
    private void checkCanManage(GroupMember member) {
        if (!GroupRole.from(member.getRole()).canManage()) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Only owner and admins can perform this action");
        }
    }

    /** @throws BusinessException 如果成员当前被禁言 */
    private void checkNotMuted(GroupMember member) {
        if (member.getMuteUntil() != null && member.getMuteUntil().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "You are muted, cannot speak");
        }
    }

    /** @throws BusinessException 如果UID不对应真实用户，抛出USER_NOT_FOUND错误 */
    private void validateUidExists(Long uid) {
        try {
            userFeignClient.getUserByUid(uid);
        } catch (BusinessException e) {
            if (e.getCode() == ResultCode.USER_NOT_FOUND.getCode()) {
                throw new BusinessException(ResultCode.USER_NOT_FOUND.getCode(), "User " + uid + " not found, cannot invite");
            }
            throw e;
        }
    }

    private void sendSystemMessage(Long gid, Long uid, String type) {
        try {
            User user = userFeignClient.getUserByUid(uid);
            String name = user != null ? user.getNickname() : String.valueOf(uid);
            java.util.Map<String, Object> contentMap = new java.util.LinkedHashMap<>();
            contentMap.put("type", type);
            contentMap.put("uid", String.valueOf(uid));
            contentMap.put("name", name);
            String content = MAPPER.writeValueAsString(contentMap);
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("gid", gid);
            body.put("fromUid", uid);
            body.put("content", content);
            messageFeignClient.sendSystemMessage(body);
        } catch (Exception e) {
            log.warn("Failed to send system message: type={}, gid={}, uid={}", type, gid, uid);
        }
    }

    private void assignMemberIndex(Long gid, Long uid) {
        String idxKey = RedisKeyConstants.GROUP_MEMBER_IDX_PREFIX + gid;
        if (redisTemplate.opsForHash().hasKey(idxKey, String.valueOf(uid))) {
            return;
        }
        Long idx = redisTemplate.opsForValue().increment(RedisKeyConstants.GROUP_MEMBER_IDX_PREFIX + RedisKeyConstants.SEQ_PREFIX + gid);
        redisTemplate.opsForHash().put(idxKey, String.valueOf(uid), String.valueOf(idx));
        redisTemplate.expire(RedisKeyConstants.GROUP_MEMBER_IDX_PREFIX + gid, BusinessConstants.REDIS_WEEK_TTL);
        redisTemplate.expire(RedisKeyConstants.GROUP_MEMBER_IDX_PREFIX + RedisKeyConstants.SEQ_PREFIX + gid, BusinessConstants.REDIS_WEEK_TTL);
    }

    private void removeMemberIndex(Long gid, Long uid) {
        redisTemplate.opsForHash().delete(RedisKeyConstants.GROUP_MEMBER_IDX_PREFIX + gid, String.valueOf(uid));
    }
}
