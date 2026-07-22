package me.aster.echochat.group.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.common.exception.BusinessException;
import me.aster.echochat.common.result.ResultCode;
import me.aster.echochat.group.entity.GroupInfo;
import me.aster.echochat.group.entity.GroupInvite;
import me.aster.echochat.group.entity.GroupMember;
import me.aster.echochat.group.entity.GroupRole;
import me.aster.echochat.group.mapper.GroupInfoMapper;
import me.aster.echochat.group.mapper.GroupInviteMapper;
import me.aster.echochat.group.mapper.GroupMemberMapper;
import me.aster.echochat.group.service.InviteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * {@link InviteService} 的实现，用于创建和获取群组邀请链接。
 * @author AsterWinston
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InviteServiceImpl implements InviteService {

    private static final int MAX_EXPIRE_HOURS = 72;

    private final GroupInviteMapper groupInviteMapper;
    private final GroupInfoMapper groupInfoMapper;
    private final GroupMemberMapper groupMemberMapper;

    /**
     * 为群组创建一次性邀请链接。邀请码为16位随机字符串，
     * 有效时长1-72小时（默认24）。仅所有者和管理员可创建。
     *
     * @param uid         请求用户
     * @param gid         群组ID
     * @param expireHours 有效时长（小时），范围1-72
     * @return 包含code、expireAt和gid的map
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createInvite(Long uid, Long gid, int expireHours) {
        GroupInfo group = groupInfoMapper.selectById(gid);
        if (group == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "Group not found");
        }

        GroupMember member = groupMemberMapper.findByGidAndUid(gid, uid);
        if (member == null) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Not a member of this group");
        }
        if (!GroupRole.from(member.getRole()).canManage()) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Only owner and admins can create invite links");
        }

        if (expireHours <= 0 || expireHours > MAX_EXPIRE_HOURS) {
            expireHours = 24;
        }

        GroupInvite invite = new GroupInvite();
        invite.setGid(gid);
        invite.setCode(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        invite.setExpireAt(LocalDateTime.now().plusHours(expireHours));
        invite.setUsed(0);
        invite.setCreatedAt(LocalDateTime.now());
        groupInviteMapper.insert(invite);

        log.info("Invite created: gid={}, code={}, expireHours={}", gid, invite.getCode(), expireHours);

        Map<String, Object> result = new LinkedHashMap<>(16);
        result.put("code", invite.getCode());
        result.put("expireAt", invite.getExpireAt());
        result.put("gid", gid);
        return result;
    }

    /**
     * 根据邀请码获取邀请链接详情。返回群组名称、过期时间和使用状态，供客户端在加入前展示。
     *
     * @param code 邀请码
     * @return 包含gid、groupName、expireAt、used、expired的map
     */
    @Override
    public Map<String, Object> getInviteInfo(String code) {
        GroupInvite invite = groupInviteMapper.findByCode(code);
        if (invite == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "Invite link not found");
        }

        GroupInfo group = groupInfoMapper.selectById(invite.getGid());
        Map<String, Object> result = new LinkedHashMap<>(16);
        result.put("code", invite.getCode());
        result.put("gid", invite.getGid());
        result.put("groupName", group != null ? group.getName() : null);
        result.put("expireAt", invite.getExpireAt());
        result.put("used", invite.getUsed());
        result.put("expired", invite.getExpireAt().isBefore(LocalDateTime.now()));
        return result;
    }
}
