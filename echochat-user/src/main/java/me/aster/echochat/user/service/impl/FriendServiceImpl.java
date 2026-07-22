package me.aster.echochat.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.common.constant.BusinessConstants;
import me.aster.echochat.common.entity.User;
import me.aster.echochat.common.exception.BusinessException;
import me.aster.echochat.common.result.ResultCode;
import me.aster.echochat.common.util.SnowflakeIdGenerator;
import me.aster.echochat.user.entity.Blacklist;
import me.aster.echochat.user.entity.Friend;
import me.aster.echochat.user.entity.FriendRequest;
import me.aster.echochat.user.mapper.BlacklistMapper;
import me.aster.echochat.user.mapper.FriendMapper;
import me.aster.echochat.user.mapper.FriendRequestMapper;
import me.aster.echochat.user.mapper.UserMapper;
import me.aster.echochat.user.mq.NotificationEventPublisher;
import me.aster.echochat.user.service.FriendService;
import me.aster.echochat.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 好友关系和黑名单管理的服务实现。
 * 处理好友请求（发送、接受、拒绝）、好友列表增删改查、分组设置和黑名单操作。
 * @author AsterWinston
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FriendServiceImpl implements FriendService {

    private final FriendMapper friendMapper;
    private final FriendRequestMapper friendRequestMapper;
    private final BlacklistMapper blacklistMapper;
    private final UserMapper userMapper;
    private final UserService userService;
    private final SnowflakeIdGenerator idGenerator;
    private final NotificationEventPublisher notificationEventPublisher;

    /**
     * 发送好友请求，验证条件：不能加自己为好友、不能已是好友、不能在黑名单中、不能存在重复请求。
     *
     * @param fromUid 发送方用户 ID
     * @param toUid   接收方用户 ID
     * @param message 可选的问候消息
     * @throws BusinessException 如果验证失败
     */
    @Override
    @Transactional
    public void sendRequest(Long fromUid, Long toUid, String message) {
        if (fromUid.equals(toUid)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "Cannot add yourself as friend");
        }

        User targetUser = userMapper.selectById(toUid);
        if (targetUser == null || (targetUser.getIsDeleted() != null && targetUser.getIsDeleted() == 1)) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        LambdaQueryWrapper<Friend> friendWrapper = new LambdaQueryWrapper<>();
        friendWrapper.eq(Friend::getUserUid, fromUid).eq(Friend::getFriendUid, toUid);
        if (friendMapper.selectCount(friendWrapper) > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "Already friends");
        }

        if (isBlacklisted(fromUid, toUid)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "You have blocked this user");
        }

        if (isBlacklisted(toUid, fromUid)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "You have been blocked by this user");
        }

        FriendRequest existing = friendRequestMapper.findPendingRequest(fromUid, toUid);
        if (existing != null) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "A pending friend request already exists");
        }

        FriendRequest request = new FriendRequest();
        request.setId(idGenerator.nextId());
        request.setFromUid(fromUid);
        request.setToUid(toUid);
        request.setMessage(message);
        request.setStatus(BusinessConstants.REQUEST_STATUS_PENDING);
        request.setExpireAt(LocalDateTime.now().plusDays(BusinessConstants.FRIEND_REQUEST_EXPIRY_DAYS));
        request.setCreatedAt(LocalDateTime.now());
        friendRequestMapper.insert(request);

        notificationEventPublisher.publishAfterCommit(toUid, "friend_request",
                "New Friend Request", "Someone wants to be your friend", request.getId());
    }

    /**
     * 接受待处理的好友请求并创建双向好友记录。
     *
     * @param requestId  好友请求 ID
     * @param currentUid 接受请求的用户（必须是接收方）
     * @throws BusinessException 如果请求不存在、无权限、已处理或已过期
     */
    @Override
    @Transactional
    public void acceptRequest(Long requestId, Long currentUid) {
        FriendRequest request = friendRequestMapper.selectById(requestId);
        if (request == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "Friend request not found");
        }
        if (!request.getToUid().equals(currentUid)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "No permission to handle this request");
        }
        if (!BusinessConstants.REQUEST_STATUS_PENDING.equals(request.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "Request already handled");
        }
        if (LocalDateTime.now().isAfter(request.getExpireAt())) {
            int expired = friendRequestMapper.updateStatus(requestId, BusinessConstants.REQUEST_STATUS_EXPIRED, BusinessConstants.REQUEST_STATUS_PENDING);
            if (expired == 0) {
                throw new BusinessException(ResultCode.CONFLICT.getCode(), "Request already handled");
            }
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "Friend request expired");
        }

        int updated = friendRequestMapper.updateStatus(requestId, BusinessConstants.REQUEST_STATUS_ACCEPTED, BusinessConstants.REQUEST_STATUS_PENDING);
        if (updated == 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "Request already handled");
        }

        Friend friend1 = new Friend();
        friend1.setId(idGenerator.nextId());
        friend1.setUserUid(request.getFromUid());
        friend1.setFriendUid(request.getToUid());
        friend1.setCreatedAt(LocalDateTime.now());
        friendMapper.insert(friend1);

        Friend friend2 = new Friend();
        friend2.setId(idGenerator.nextId());
        friend2.setUserUid(request.getToUid());
        friend2.setFriendUid(request.getFromUid());
        friend2.setCreatedAt(LocalDateTime.now());
        friendMapper.insert(friend2);

        notificationEventPublisher.publishAfterCommit(request.getFromUid(), "friend_accept",
                "Friend request accepted", "Your friend request was accepted", requestId);
    }

    /**
     * 拒绝待处理的好友请求。
     *
     * @param requestId  好友请求 ID
     * @param currentUid 拒绝请求的用户（必须是接收方）
     * @throws BusinessException 如果请求不存在、无权限或已处理
     */
    @Override
    @Transactional
    public void rejectRequest(Long requestId, Long currentUid) {
        FriendRequest request = friendRequestMapper.selectById(requestId);
        if (request == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "Friend request not found");
        }
        if (!request.getToUid().equals(currentUid)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "No permission to handle this request");
        }
        if (!BusinessConstants.REQUEST_STATUS_PENDING.equals(request.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "Request already handled");
        }
        request.setStatus(BusinessConstants.REQUEST_STATUS_REJECTED);
        request.setHandledAt(LocalDateTime.now());
        friendRequestMapper.updateById(request);
    }

    /**
     * 获取收到的好友请求，可按状态过滤。
     *
     * @param uid    用户 ID
     * @param status 可选的状态过滤条件（null 表示返回所有）
     * @return 收到的好友请求列表
     */
    @Override
    public List<FriendRequest> getReceivedRequests(Long uid, String status) {
        if (status != null && !status.isEmpty()) {
            return friendRequestMapper.findReceivedRequestsByStatus(uid, status);
        }
        return friendRequestMapper.findReceivedRequests(uid);
    }

    /**
     * 获取发出的好友请求，可按状态过滤。
     *
     * @param uid    用户 ID
     * @param status 可选的状态过滤条件（null 表示返回所有）
     * @return 发出的好友请求列表
     */
    @Override
    public List<FriendRequest> getSentRequests(Long uid, String status) {
        if (status != null && !status.isEmpty()) {
            return friendRequestMapper.findSentRequestsByStatus(uid, status);
        }
        return friendRequestMapper.findSentRequests(uid);
    }

    /**
     * 获取好友列表及个人资料信息，跳过已注销用户。
     *
     * @param uid 用户 ID
     * @return 包含 uid、nickname、avatar、signature、gender、status、groupName、memo、createdAt 的好友资料列表
     */
    @Override
    public List<Map<String, Object>> getFriendList(Long uid) {
        List<Friend> friends = friendMapper.findByUid(uid);
        if (friends.isEmpty()) {
            return List.of();
        }
        List<Long> friendUids = friends.stream().map(Friend::getFriendUid).toList();
        List<User> users = userMapper.selectBatchIds(friendUids);
        Map<Long, User> userMap = new LinkedHashMap<>(16);
        for (User u : users) {
            if (u != null && (u.getIsDeleted() == null || u.getIsDeleted() != 1)) {
                userMap.put(u.getUid(), u);
            }
        }
        Map<Long, Boolean> onlineMap = userService.getOnlineStatuses(friendUids);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Friend friend : friends) {
            User friendUser = userMap.get(friend.getFriendUid());
            if (friendUser == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>(16);
            item.put("uid", String.valueOf(friendUser.getUid()));
            item.put("nickname", friendUser.getNickname());
            item.put("avatar", friendUser.getAvatar());
            item.put("signature", friendUser.getSignature());
            item.put("gender", friendUser.getGender());
            item.put("status", Boolean.TRUE.equals(onlineMap.get(friendUser.getUid())) ? 1 : 0);
            item.put("groupName", friend.getGroupName());
            item.put("memo", friend.getMemo());
            item.put("createdAt", friend.getCreatedAt());
            result.add(item);
        }
        return result;
    }

    /**
     * 更新特定好友的分组/类别和/或备注。
     *
     * @param uid       用户 ID
     * @param friendUid 好友的用户 ID
     * @param groupName 新的分组名称（null 表示保持不变）
     * @param memo      新的备注（null 表示保持不变）
     * @throws BusinessException 如果好友关系不存在
     */
    @Override
    @Transactional
    public void updateGroup(Long uid, Long friendUid, String groupName, String memo) {
        LambdaQueryWrapper<Friend> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Friend::getUserUid, uid).eq(Friend::getFriendUid, friendUid);
        Friend friend = friendMapper.selectOne(wrapper);
        if (friend == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "Friendship not found");
        }
        if (groupName != null) {
            friend.setGroupName(groupName);
        }
        if (memo != null) {
            friend.setMemo(memo);
        }
        friendMapper.updateById(friend);
    }

    /**
     * 获取对特定好友的备注，若未设置则返回 null。
     *
     * @param uid       用户 ID
     * @param friendUid 好友的用户 ID
     * @return 备注字符串，或 null
     */
    @Override
    public String getFriendMemo(Long uid, Long friendUid) {
        LambdaQueryWrapper<Friend> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Friend::getUserUid, uid).eq(Friend::getFriendUid, friendUid);
        Friend friend = friendMapper.selectOne(wrapper);
        return friend != null ? friend.getMemo() : null;
    }

    /**
     * 批量获取多个好友关系的备注，单次查询。
     *
     * @param uid        用户 ID
     * @param friendUids 要查询的好友 UID 列表
     * @return friendUid 到 memo 的映射，没有备注的条目会被省略
     */
    @Override
    public Map<Long, String> getFriendMemos(Long uid, List<Long> friendUids) {
        if (friendUids == null || friendUids.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<Friend> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Friend::getUserUid, uid).in(Friend::getFriendUid, friendUids);
        Map<Long, String> memos = new LinkedHashMap<>(16);
        for (Friend friend : friendMapper.selectList(wrapper)) {
            if (friend.getMemo() != null && !friend.getMemo().isEmpty()) {
                memos.put(friend.getFriendUid(), friend.getMemo());
            }
        }
        return memos;
    }

    /**
     * 双向删除好友关系（同时删除 userUid->friendUid 和 friendUid->userUid 方向的记录）。
     *
     * @param uid       用户 ID
     * @param friendUid 要删除的好友用户 ID
     */
    @Override
    @Transactional
    public void deleteFriend(Long uid, Long friendUid) {
        LambdaQueryWrapper<Friend> wrapper1 = new LambdaQueryWrapper<>();
        wrapper1.eq(Friend::getUserUid, uid).eq(Friend::getFriendUid, friendUid);
        friendMapper.delete(wrapper1);

        LambdaQueryWrapper<Friend> wrapper2 = new LambdaQueryWrapper<>();
        wrapper2.eq(Friend::getUserUid, friendUid).eq(Friend::getFriendUid, uid);
        friendMapper.delete(wrapper2);
    }

    /**
     * 将用户添加到当前用户的黑名单，验证条件：不能屏蔽自己、目标用户必须存在、不能重复屏蔽。
     *
     * @param uid        用户 ID
     * @param blockedUid 要屏蔽的用户 ID
     * @throws BusinessException 如果屏蔽自己、用户不存在或已在黑名单中
     */
    @Override
    @Transactional
    public void addToBlacklist(Long uid, Long blockedUid) {
        if (uid.equals(blockedUid)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "Cannot block yourself");
        }

        User targetUser = userMapper.selectById(blockedUid);
        if (targetUser == null || (targetUser.getIsDeleted() != null && targetUser.getIsDeleted() == 1)) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        if (isBlacklisted(uid, blockedUid)) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "User already in blacklist");
        }

        Blacklist blacklist = new Blacklist();
        blacklist.setId(idGenerator.nextId());
        blacklist.setUid(uid);
        blacklist.setBlockedUid(blockedUid);
        blacklist.setCreatedAt(LocalDateTime.now());
        blacklistMapper.insert(blacklist);
    }

    /**
     * 将用户从当前用户的黑名单中移除。
     *
     * @param uid        用户 ID
     * @param blockedUid 要解除屏蔽的用户 ID
     */
    @Override
    @Transactional
    public void removeFromBlacklist(Long uid, Long blockedUid) {
        LambdaQueryWrapper<Blacklist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Blacklist::getUid, uid).eq(Blacklist::getBlockedUid, blockedUid);
        blacklistMapper.delete(wrapper);
    }

    /**
     * 获取完整的黑名单及被屏蔽用户的个人资料信息。
     *
     * @param uid 用户 ID
     * @return 包含 uid、nickname、avatar 和屏蔽时间戳的被屏蔽用户列表
     */
    @Override
    public List<Map<String, Object>> getBlacklist(Long uid) {
        List<Blacklist> blacklist = blacklistMapper.findByUid(uid);
        if (blacklist.isEmpty()) {
            return List.of();
        }
        List<Long> blockedUids = blacklist.stream().map(Blacklist::getBlockedUid).toList();
        List<User> users = userMapper.selectBatchIds(blockedUids);
        Map<Long, User> userMap = new LinkedHashMap<>(16);
        for (User u : users) {
            if (u != null) {
                userMap.put(u.getUid(), u);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Blacklist item : blacklist) {
            User blockedUser = userMap.get(item.getBlockedUid());
            if (blockedUser == null) {
                continue;
            }
            Map<String, Object> map = new LinkedHashMap<>(16);
            map.put("uid", String.valueOf(blockedUser.getUid()));
            map.put("nickname", blockedUser.getNickname());
            map.put("avatar", blockedUser.getAvatar());
            map.put("createdAt", item.getCreatedAt());
            result.add(map);
        }
        return result;
    }

    /**
     * 检查某用户是否被另一用户屏蔽。
     *
     * @param uid        可能屏蔽其他用户的用户 ID
     * @param blockedUid 可能被屏蔽的用户 ID
     * @return 如果存在黑名单条目则返回 true
     */
    @Override
    public boolean isBlacklisted(Long uid, Long blockedUid) {
        Blacklist record = blacklistMapper.findByUidAndBlockedUid(uid, blockedUid);
        return record != null;
    }

    /**
     * 检查两个用户是否为好友（单向检查：uid1 是否将 uid2 视为好友）。
     *
     * @param uid1 第一个用户 ID
     * @param uid2 第二个用户 ID
     * @return 如果 uid1 将 uid2 视为好友则返回 true
     */
    @Override
    public boolean areFriends(Long uid1, Long uid2) {
        LambdaQueryWrapper<Friend> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Friend::getUserUid, uid1).eq(Friend::getFriendUid, uid2);
        return friendMapper.selectCount(wrapper) > 0;
    }

    /**
     * 获取指定用户的好友 UID 列表。
     *
     * @param uid 用户 ID
     * @return 好友用户 ID 列表
     */
    @Override
    public List<Long> getFriendUids(Long uid) {
        return friendMapper.findFriendUids(uid);
    }

    /**
     * 获取用户黑名单中的所有用户 UID。
     *
     * @param uid 用户 ID
     * @return 黑名单用户 ID 列表
     */
    @Override
    public List<Long> getBlacklistUids(Long uid) {
        return blacklistMapper.findByUid(uid).stream()
                .map(Blacklist::getBlockedUid).toList();
    }

    /**
     * 定时任务，每小时清理一次过期的好友请求。
     */
    @Scheduled(fixedRate = 3600000)
    public void expireRequests() {
        List<FriendRequest> expired = friendRequestMapper.selectList(
            new LambdaQueryWrapper<FriendRequest>()
                .eq(FriendRequest::getStatus, BusinessConstants.REQUEST_STATUS_PENDING)
                .lt(FriendRequest::getExpireAt, LocalDateTime.now()));
        for (FriendRequest req : expired) {
            friendRequestMapper.updateStatus(req.getId(), BusinessConstants.REQUEST_STATUS_EXPIRED, BusinessConstants.REQUEST_STATUS_PENDING);
        }
        if (!expired.isEmpty()) {
            log.info("Expired {} friend requests", expired.size());
        }
    }
}
