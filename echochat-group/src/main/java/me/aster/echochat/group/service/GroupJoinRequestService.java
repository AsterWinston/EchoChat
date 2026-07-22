package me.aster.echochat.group.service;

import me.aster.echochat.group.entity.GroupJoinRequest;

import java.util.List;

/**
 * 群组加入申请操作的服务接口，包括申请、批准、拒绝、查询待处理申请以及获取用户自己的申请。
 * @author AsterWinston
 */
public interface GroupJoinRequestService {

    GroupJoinRequest apply(Long uid, Long gid, String message);

    GroupJoinRequest approve(Long processorUid, Long requestId);

    GroupJoinRequest reject(Long processorUid, Long requestId);

    List<GroupJoinRequest> getPendingRequests(Long gid);

    GroupJoinRequest getMyRequest(Long uid, Long gid);
}
