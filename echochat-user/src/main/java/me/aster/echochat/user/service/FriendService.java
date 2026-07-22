package me.aster.echochat.user.service;

import me.aster.echochat.common.exception.BusinessException;
import me.aster.echochat.user.entity.FriendRequest;
import java.util.List;
import java.util.Map;

/**
 * 好友关系和黑名单管理的服务接口。
 * @author AsterWinston
 */
public interface FriendService {

    /**
     * 从一个用户向另一个用户发送好友请求。
     *
     * @param fromUid 发送方用户 ID
     * @param toUid   接收方用户 ID
     * @param message 可选的问候消息
     * @throws BusinessException 如果试图添加自己、用户不存在、已是好友、被屏蔽或存在重复请求
     */
    void sendRequest(Long fromUid, Long toUid, String message);

    /**
     * 接受待处理的好友请求并建立双向好友关系。
     *
     * @param requestId  好友请求 ID
     * @param currentUid 接受请求的用户（必须是接收方）
     * @throws BusinessException 如果请求不存在、无权限、已处理或已过期
     */
    void acceptRequest(Long requestId, Long currentUid);

    /**
     * 拒绝待处理的好友请求。
     *
     * @param requestId  好友请求 ID
     * @param currentUid 拒绝请求的用户（必须是接收方）
     * @throws BusinessException 如果请求不存在、无权限或已处理
     */
    void rejectRequest(Long requestId, Long currentUid);

    /**
     * 获取已收到的好友请求，可按状态过滤。
     *
     * @param uid    用户 ID
     * @param status 可选的状态过滤条件（pending、accepted、rejected）
     * @return 已收到的好友请求列表
     */
    List<FriendRequest> getReceivedRequests(Long uid, String status);

    /**
     * 获取已发出的好友请求，可按状态过滤。
     *
     * @param uid    用户 ID
     * @param status 可选的状态过滤条件（pending、accepted、rejected）
     * @return 已发出的好友请求列表
     */
    List<FriendRequest> getSentRequests(Long uid, String status);

    /**
     * 获取用户的好友列表及个人资料信息。
     *
     * @param uid 用户 ID
     * @return 包含分组名称和其他详细信息的好友资料列表
     */
    List<Map<String, Object>> getFriendList(Long uid);

    /**
     * 更新好友的分组/类别和/或备注。
     *
     * @param uid       用户 ID
     * @param friendUid 好友的用户 ID
     * @param groupName 新的分组名称（null 表示保持不变）
     * @param memo      新的备注/别名（null 表示保持不变）
     * @throws BusinessException 如果好友关系不存在
     */
    void updateGroup(Long uid, Long friendUid, String groupName, String memo);

    /**
     * 双向删除好友关系。
     *
     * @param uid       用户 ID
     * @param friendUid 要删除的好友用户 ID
     */
    void deleteFriend(Long uid, Long friendUid);

    /**
     * 将用户添加到当前用户的黑名单。
     *
     * @param uid        用户 ID
     * @param blockedUid 要屏蔽的用户 ID
     * @throws BusinessException 如果试图屏蔽自己、用户不存在或已在黑名单中
     */
    void addToBlacklist(Long uid, Long blockedUid);

    /**
     * 将用户从当前用户的黑名单中移除。
     *
     * @param uid        用户 ID
     * @param blockedUid 要解除屏蔽的用户 ID
     */
    void removeFromBlacklist(Long uid, Long blockedUid);

    /**
     * 获取用户的完整黑名单及基本信息。
     *
     * @param uid 用户 ID
     * @return 包含 uid、nickname 和 avatar 的被屏蔽用户列表
     */
    List<Map<String, Object>> getBlacklist(Long uid);

    /**
     * 检查某用户是否屏蔽了另一用户。
     *
     * @param uid        用户 ID
     * @param blockedUid 可能被屏蔽的用户 ID
     * @return 如果已屏蔽则返回 true
     */
    boolean isBlacklisted(Long uid, Long blockedUid);

    /**
     * 单向检查 uid1 是否将 uid2 视为好友。
     *
     * @param uid1 第一个用户 ID
     * @param uid2 第二个用户 ID
     * @return 如果 uid1 将 uid2 视为好友则返回 true
     */
    boolean areFriends(Long uid1, Long uid2);

    /**
     * 获取用户的好友 UID 列表。
     *
     * @param uid 用户 ID
     * @return 好友 UID 列表
     */
    List<Long> getFriendUids(Long uid);

    /**
     * 获取用户黑名单中的所有用户 UID。
     *
     * @param uid 用户 ID
     * @return 黑名单 UID 列表
     */
    List<Long> getBlacklistUids(Long uid);

    /**
     * 获取对特定好友的备注/别名，若未设置则返回 null。
     *
     * @param uid       用户 ID
     * @param friendUid 好友的用户 ID
     * @return 备注字符串，或 null
     */
    String getFriendMemo(Long uid, Long friendUid);

    /**
     * 批量获取多个好友关系的备注，单次查询。
     *
     * @param uid        用户 ID
     * @param friendUids 要查询的好友 UID 列表
     * @return friendUid 到 memo 的映射，没有备注的条目会被省略
     */
    java.util.Map<Long, String> getFriendMemos(Long uid, List<Long> friendUids);
}
