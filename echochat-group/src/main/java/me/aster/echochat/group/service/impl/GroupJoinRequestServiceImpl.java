package me.aster.echochat.group.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.common.constant.BusinessConstants;
import me.aster.echochat.common.exception.BusinessException;
import me.aster.echochat.common.result.ResultCode;
import me.aster.echochat.common.util.SnowflakeIdGenerator;
import me.aster.echochat.group.entity.GroupInfo;
import me.aster.echochat.group.entity.GroupJoinRequest;
import me.aster.echochat.group.entity.GroupMember;
import me.aster.echochat.group.mapper.GroupInfoMapper;
import me.aster.echochat.group.mapper.GroupJoinRequestMapper;
import me.aster.echochat.group.mapper.GroupMemberMapper;
import me.aster.echochat.group.mq.NotificationEventPublisher;
import me.aster.echochat.group.service.GroupJoinRequestService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link GroupJoinRequestService} 的实现，处理群组加入申请的提交、批准、拒绝以及向群组管理员发送通知。
 * @author AsterWinston
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupJoinRequestServiceImpl implements GroupJoinRequestService {

    private final GroupJoinRequestMapper requestMapper;
    private final GroupInfoMapper groupInfoMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final NotificationEventPublisher notificationEventPublisher;
    private final SnowflakeIdGenerator idGenerator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupJoinRequest apply(Long uid, Long gid, String message) {
        GroupInfo group = groupInfoMapper.selectById(gid);
        if (group == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "Group not found");
        }

        GroupMember existingMember = groupMemberMapper.findByGidAndUid(gid, uid);
        if (existingMember != null) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "Already a member of this group");
        }

        GroupJoinRequest existingRequest = requestMapper.findByGidAndUid(gid, uid);
        if (existingRequest != null && BusinessConstants.REQUEST_STATUS_PENDING.equals(existingRequest.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "You already have a pending join request");
        }

        GroupJoinRequest request = new GroupJoinRequest();
        request.setId(idGenerator.nextId());
        request.setGid(gid);
        request.setUid(uid);
        request.setStatus(BusinessConstants.REQUEST_STATUS_PENDING);
        request.setMessage(message);
        request.setCreatedAt(LocalDateTime.now());
        requestMapper.insert(request);

        notifyAdmins(group, uid);

        return request;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupJoinRequest approve(Long processorUid, Long requestId) {
        GroupJoinRequest request = requestMapper.selectById(requestId);
        if (request == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "Join request not found");
        }
        if (!BusinessConstants.REQUEST_STATUS_PENDING.equals(request.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "This request has already been processed");
        }

        GroupMember processor = groupMemberMapper.findByGidAndUid(request.getGid(), processorUid);
        boolean isOwner = processor != null && BusinessConstants.ROLE_OWNER.equals(processor.getRole());
        boolean isAdmin = processor != null && BusinessConstants.ROLE_ADMIN.equals(processor.getRole());
        if (!isOwner && !isAdmin) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Only owner and admins can process join requests");
        }

        Long applicantUid = request.getUid();
        GroupMember existingMember = groupMemberMapper.findByGidAndUid(request.getGid(), applicantUid);
        if (existingMember != null) {
            request.setStatus("approved");
            request.setProcessedBy(processorUid);
            request.setProcessedAt(LocalDateTime.now());
            requestMapper.updateById(request);
            return request;
        }

        GroupMember newMember = new GroupMember();
        newMember.setGid(request.getGid());
        newMember.setUid(applicantUid);
        newMember.setRole(BusinessConstants.ROLE_MEMBER);
        newMember.setJoinedAt(LocalDateTime.now());
        groupMemberMapper.insert(newMember);

        request.setStatus("approved");
        request.setProcessedBy(processorUid);
        request.setProcessedAt(LocalDateTime.now());
        requestMapper.updateById(request);

        String approvedContent;
        try {
            GroupInfo group = groupInfoMapper.selectById(request.getGid());
            approvedContent = "Your request to join " + (group != null ? group.getName() : "the group") + " has been approved";
        } catch (Exception e) {
            approvedContent = "Your request to join the group has been approved";
        }
        notificationEventPublisher.publishAfterCommit(applicantUid, "group_join_approved",
                "Join request approved", approvedContent, request.getGid());

        return request;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupJoinRequest reject(Long processorUid, Long requestId) {
        GroupJoinRequest request = requestMapper.selectById(requestId);
        if (request == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "Join request not found");
        }
        if (!BusinessConstants.REQUEST_STATUS_PENDING.equals(request.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "This request has already been processed");
        }

        GroupMember processor = groupMemberMapper.findByGidAndUid(request.getGid(), processorUid);
        boolean isOwner = processor != null && BusinessConstants.ROLE_OWNER.equals(processor.getRole());
        boolean isAdmin = processor != null && BusinessConstants.ROLE_ADMIN.equals(processor.getRole());
        if (!isOwner && !isAdmin) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Only owner and admins can process join requests");
        }

        request.setStatus(BusinessConstants.REQUEST_STATUS_REJECTED);
        request.setProcessedBy(processorUid);
        request.setProcessedAt(LocalDateTime.now());
        requestMapper.updateById(request);

        return request;
    }

    @Override
    public List<GroupJoinRequest> getPendingRequests(Long gid) {
        return requestMapper.findPendingByGid(gid);
    }

    @Override
    public GroupJoinRequest getMyRequest(Long uid, Long gid) {
        return requestMapper.findByGidAndUid(gid, uid);
    }

    private void notifyAdmins(GroupInfo group, Long applicantUid) {
        List<GroupMember> members = groupMemberMapper.findByGid(group.getGid());
        for (GroupMember member : members) {
            if (BusinessConstants.ROLE_MEMBER.equals(member.getRole())) {
                continue;
            }
            if (member.getUid().equals(applicantUid)) {
                continue;
            }
            notificationEventPublisher.publishAfterCommit(member.getUid(), "group_join_request",
                    "New join request", "A user wants to join " + group.getName(), group.getGid());
        }
    }
}
