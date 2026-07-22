package me.aster.echochat.group.service;

import me.aster.echochat.group.entity.GroupJoinRequest;

import java.util.List;

/**
 * 群组加入申请操作的服务接口，包括申请、批准、拒绝、查询待处理申请以及获取用户自己的申请。
 * @author AsterWinston
 */
public interface GroupJoinRequestService {

    /**
     * 提交加群申请。
     *
     * @param uid     申请人用户ID
     * @param gid     目标群组ID
     * @param message 申请附言
     * @return 创建的申请记录
     */
    GroupJoinRequest apply(Long uid, Long gid, String message);

    /**
     * 批准加群申请。
     *
     * @param processorUid 审批人用户ID
     * @param requestId    申请记录ID
     * @return 更新后的申请记录
     */
    GroupJoinRequest approve(Long processorUid, Long requestId);

    /**
     * 拒绝加群申请。
     *
     * @param processorUid 审批人用户ID
     * @param requestId    申请记录ID
     * @return 更新后的申请记录
     */
    GroupJoinRequest reject(Long processorUid, Long requestId);

    /**
     * 获取指定群组中所有待处理的申请列表。
     *
     * @param gid 群组ID
     * @return 待处理申请列表
     */
    List<GroupJoinRequest> getPendingRequests(Long gid);

    /**
     * 获取用户在指定群组中的最新申请。
     *
     * @param uid 用户ID
     * @param gid 群组ID
     * @return 最新的申请记录，不存在则返回null
     */
    GroupJoinRequest getMyRequest(Long uid, Long gid);
}
