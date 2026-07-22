package me.aster.echochat.group.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.common.constant.BusinessConstants;
import me.aster.echochat.common.exception.BusinessException;
import me.aster.echochat.common.result.ResultCode;
import me.aster.echochat.common.util.SnowflakeIdGenerator;
import me.aster.echochat.group.entity.GroupInfo;
import me.aster.echochat.group.entity.GroupMember;
import me.aster.echochat.group.entity.GroupRole;
import me.aster.echochat.group.mapper.GroupInfoMapper;
import me.aster.echochat.group.mapper.GroupMemberMapper;
import me.aster.echochat.group.mq.GroupIndexService;
import me.aster.echochat.group.service.GroupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * {@link GroupService} 的实现，处理群组CRUD、所有权转让和解散。
 * @author AsterWinston
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    private final GroupInfoMapper groupInfoMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final GroupIndexService groupIndexService;

    /**
     * 创建指定名称的新群组。调用者成为所有者。慢速模式默认禁用。
     */
    @Override
    @Transactional
    public GroupInfo createGroup(Long ownerUid, String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "Group name cannot be empty");
        }
        if (name.length() > BusinessConstants.GROUP_NAME_MAX_LENGTH) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "Group name cannot exceed " + BusinessConstants.GROUP_NAME_MAX_LENGTH + " characters");
        }

        GroupInfo group = new GroupInfo();
        group.setGid(idGenerator.nextId());
        group.setName(name);
        group.setOwnerUid(ownerUid);
        group.setSlowModeInterval(0);
        group.setCreatedAt(LocalDateTime.now());
        group.setUpdatedAt(LocalDateTime.now());
        groupInfoMapper.insert(group);

        GroupMember owner = new GroupMember();
        owner.setGid(group.getGid());
        owner.setUid(ownerUid);
        owner.setRole(GroupRole.OWNER.name().toLowerCase());
        owner.setJoinedAt(LocalDateTime.now());
        groupMemberMapper.insert(owner);

        log.info("Group created: gid={}, name={}, owner={}", group.getGid(), name, ownerUid);
        groupIndexService.syncGroupToEs(group);
        return group;
    }

    /**
     * 根据群组ID获取群组信息。
     *
     * @param gid 群组ID
     * @return 群组实体
     * @throws BusinessException 如果群组不存在
     */
    @Override
    public GroupInfo getGroupInfo(Long gid) {
        GroupInfo group = groupInfoMapper.selectById(gid);
        if (group == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "Group not found");
        }
        return group;
    }

    /**
     * 批量查询群组信息；不存在的群组会被跳过。
     *
     * @param gids 群组ID列表
     * @return 匹配的群组列表
     */
    @Override
    public List<GroupInfo> getGroupInfos(List<Long> gids) {
        if (gids == null || gids.isEmpty()) {
            return List.of();
        }
        return groupInfoMapper.selectBatchIds(gids);
    }

    /**
     * 更新群组元数据（名称、头像、公告、慢速模式间隔）。仅所有者和管理员可调用。
     */
    @Override
    @Transactional
    public GroupInfo updateGroup(Long uid, Long gid, Map<String, Object> updates) {
        GroupMember member = groupMemberMapper.findByGidAndUid(gid, uid);
        if (member == null) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Not a member of this group");
        }
        String role = member.getRole();
        if (!GroupRole.from(role).canManage()) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Only owner and admins can edit group info");
        }

        GroupInfo group = groupInfoMapper.selectById(gid);
        if (group == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "Group not found");
        }

        GroupInfo update = new GroupInfo();
        update.setGid(gid);
        update.setUpdatedAt(LocalDateTime.now());

        if (updates.containsKey("name")) {
            String name = (String) updates.get("name");
            if (name != null && !name.isBlank() && name.length() <= BusinessConstants.GROUP_NAME_MAX_LENGTH) {
                update.setName(name);
                group.setName(name);
            }
        }
        if (updates.containsKey("avatar")) {
            String avatar = (String) updates.get("avatar");
            update.setAvatar(avatar);
            group.setAvatar(avatar);
        }
        if (updates.containsKey("announcement")) {
            update.setAnnouncement((String) updates.get("announcement"));
            group.setAnnouncement((String) updates.get("announcement"));
        }
        if (updates.containsKey("slowModeInterval")) {
            Integer interval = (Integer) updates.get(BusinessConstants.MEMBERSHIP_KEY_SLOW_MODE_INTERVAL);
            update.setSlowModeInterval(interval);
            group.setSlowModeInterval(interval);
        }

        groupInfoMapper.updateById(update);
        log.info("Group updated: gid={}, by={}", gid, uid);
        groupIndexService.syncGroupToEs(group);
        return group;
    }

    /**
     * 彻底解散群组。仅所有者可解散。所有成员被移除，群组记录被删除。
     */
    @Override
    @Transactional
    public void dissolveGroup(Long uid, Long gid) {
        GroupInfo group = groupInfoMapper.selectById(gid);
        if (group == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "Group not found");
        }
        if (!group.getOwnerUid().equals(uid)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Only owner can dissolve the group");
        }

        groupMemberMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getGid, gid));
        groupInfoMapper.deleteById(gid);
        log.info("Group dissolved: gid={}", gid);
    }

    /**
     * 将群组所有权转让给另一个成员。仅当前所有者可转让。原所有者变为管理员，新所有者成为所有者。
     */
    @Override
    @Transactional
    public void transferOwner(Long uid, Long gid, Long newOwnerUid) {
        GroupInfo group = groupInfoMapper.selectById(gid);
        if (group == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "Group not found");
        }
        if (!group.getOwnerUid().equals(uid)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Only owner can transfer");
        }

        GroupMember newOwner = groupMemberMapper.findByGidAndUid(gid, newOwnerUid);
        if (newOwner == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "Target user is not in the group");
        }

        GroupMember oldOwner = groupMemberMapper.findByGidAndUid(gid, uid);
        oldOwner.setRole(GroupRole.ADMIN.name().toLowerCase());
        groupMemberMapper.updateById(oldOwner);

        newOwner.setRole(GroupRole.OWNER.name().toLowerCase());
        groupMemberMapper.updateById(newOwner);

        group.setOwnerUid(newOwnerUid);
        group.setUpdatedAt(LocalDateTime.now());
        groupInfoMapper.updateById(group);
        log.info("Group ownership transferred: gid={}, from={}, to={}", gid, uid, newOwnerUid);
    }

    /**
     * 获取指定用户拥有的所有群组。
     *
     * @param uid 用户ID
     * @return 用户为所有者的群组列表
     */
    @Override
    public List<GroupInfo> getOwnedGroups(Long uid) {
        return groupInfoMapper.findByOwnerUid(uid);
    }

    /**
     * 获取指定用户已加入的所有群组（包括作为所有者的群组）。
     *
     * @param uid 用户ID
     * @return 用户为成员的群组列表
     */
    @Override
    public List<GroupInfo> getJoinedGroups(Long uid) {
        return groupInfoMapper.findByMemberUid(uid);
    }

    @Override
    public List<GroupInfo> searchGroups(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return groupInfoMapper.searchByName(keyword);
    }

    @Override
    public void muteAll(Long uid, Long gid, boolean mute) {
        GroupInfo group = groupInfoMapper.selectById(gid);
        if (group == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "Group not found");
        }
        GroupMember member = groupMemberMapper.findByGidAndUid(gid, uid);
        if (member == null) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Not a member of this group");
        }
        String role = member.getRole();
        if (!BusinessConstants.ROLE_OWNER.equals(role) && !BusinessConstants.ROLE_ADMIN.equals(role)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Only owner and admins can manage mute-all");
        }
        group.setMuteAll(mute ? 1 : 0);
        group.setUpdatedAt(LocalDateTime.now());
        groupInfoMapper.updateById(group);
        log.info("Group mute-all {}: gid={}, uid={}", mute ? "enabled" : "disabled", gid, uid);
    }
}
