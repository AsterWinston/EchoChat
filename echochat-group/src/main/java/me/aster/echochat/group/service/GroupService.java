package me.aster.echochat.group.service;

import me.aster.echochat.group.entity.GroupInfo;
import java.util.List;
import java.util.Map;

/**
 * 群组生命周期操作的服务接口。
 * @author AsterWinston
 */
public interface GroupService {

    /**
     * 创建新群组。
     *
     * @param ownerUid 群组创建者/所有者的UID
     * @param name     群组名称
     * @return 创建好的 {@link GroupInfo}
     * @throws me.aster.echochat.common.exception.BusinessException 如果名称为空或过长
     */
    GroupInfo createGroup(Long ownerUid, String name);

    /**
     * 根据ID获取群组信息。
     *
     * @param gid 群组ID
     * @return 群组信息
     * @throws me.aster.echochat.common.exception.BusinessException 如果群组不存在
     */
    GroupInfo getGroupInfo(Long gid);

    /**
     * 批量查询群组信息；不存在的群组会被跳过。
     *
     * @param gids 群组ID列表
     * @return 匹配的群组列表
     */
    List<GroupInfo> getGroupInfos(List<Long> gids);

    /**
     * 更新群组属性。
     *
     * @param uid     操作用户ID
     * @param gid     群组ID
     * @param updates 字段名到新值的映射
     * @return 更新后的群组信息
     */
    GroupInfo updateGroup(Long uid, Long gid, Map<String, Object> updates);

    /**
     * 解散群组。
     *
     * @param uid 操作用户ID（必须是所有者）
     * @param gid 要解散的群组ID
     */
    void dissolveGroup(Long uid, Long gid);

    /**
     * 转让群组所有权。
     *
     * @param uid         当前所有者的UID
     * @param gid         群组ID
     * @param newOwnerUid 新所有者的UID
     */
    void transferOwner(Long uid, Long gid, Long newOwnerUid);

    /**
     * 获取用户拥有的所有群组。
     *
     * @param uid 用户ID
     * @return 用户拥有的群组列表
     */
    List<GroupInfo> getOwnedGroups(Long uid);

    /**
     * 获取用户已加入的所有群组。
     *
     * @param uid 用户ID
     * @return 用户所属的群组列表
     */
    List<GroupInfo> getJoinedGroups(Long uid);

    /**
     * 根据关键词搜索群组。
     *
     * @param keyword 用于匹配群名的搜索关键词
     * @return 名称包含关键词的群组列表
     */
    List<GroupInfo> searchGroups(String keyword);

    /**
     * 开启或关闭群组的全员禁言模式。
     *
     * @param uid  操作用户ID（需是所有者或管理员）
     * @param gid  群组ID
     * @param mute true为开启禁言，false为关闭
     */
    void muteAll(Long uid, Long gid, boolean mute);
}
