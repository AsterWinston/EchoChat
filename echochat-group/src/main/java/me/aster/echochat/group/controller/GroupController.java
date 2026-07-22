package me.aster.echochat.group.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.aster.echochat.common.constant.BusinessConstants;
import me.aster.echochat.common.context.UserContext;
import me.aster.echochat.common.exception.BusinessException;
import me.aster.echochat.common.result.Result;
import me.aster.echochat.common.result.ResultCode;
import me.aster.echochat.group.dto.ApplyJoinRequest;
import me.aster.echochat.group.dto.CreateGroupRequest;
import me.aster.echochat.group.dto.CreateInviteRequest;
import me.aster.echochat.group.dto.InviteMembersRequest;
import me.aster.echochat.group.dto.MuteMemberRequest;
import me.aster.echochat.group.dto.SetRoleRequest;
import me.aster.echochat.group.dto.TransferOwnerRequest;
import me.aster.echochat.group.entity.GroupInfo;
import me.aster.echochat.group.entity.GroupJoinRequest;
import me.aster.echochat.group.entity.GroupMember;
import me.aster.echochat.group.service.GroupJoinRequestService;
import me.aster.echochat.group.service.GroupMemberService;
import me.aster.echochat.group.service.GroupService;
import me.aster.echochat.group.service.InviteService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 群组管理REST控制器，包括CRUD、成员管理、角色分配、禁言控制和邀请链接。
 * @author AsterWinston
 */
@Validated
@RestController
@RequestMapping("/api/group")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;
    private final GroupMemberService memberService;
    private final InviteService inviteService;
    private final GroupJoinRequestService joinRequestService;

    /**
     * 创建新群组。调用者成为所有者。
     *
     * @param req 包含群组名称的请求
     * @return 创建好的群组信息
     */
    @PostMapping
    public Result<GroupInfo> createGroup(@Valid @RequestBody CreateGroupRequest req) {
        Long uid = UserContext.get();
        return Result.ok(groupService.createGroup(uid, req.getName()));
    }

    /**
     * 根据群组ID获取群组信息。
     *
     * @param gid 群组ID
     * @return 群组信息
     */
    @GetMapping("/{gid:[0-9]+}")
    public Result<GroupInfo> getGroupInfo(@PathVariable Long gid) {
        Long uid = UserContext.get();
        Map<String, Object> membership = memberService.checkMembership(gid, uid);
        if (membership == null || !Boolean.TRUE.equals(membership.get(BusinessConstants.MEMBERSHIP_KEY_MEMBER))) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Not a member of this group");
        }
        return Result.ok(groupService.getGroupInfo(gid));
    }

    @GetMapping("/{gid:[0-9]+}/check")
    public Result<Map<String, Object>> checkGroupAccess(@PathVariable Long gid) {
        Long uid = UserContext.get();
        GroupInfo group = groupService.getGroupInfo(gid);
        Map<String, Object> membership = memberService.checkMembership(gid, uid);
        boolean isMember = membership != null && Boolean.TRUE.equals(membership.get(BusinessConstants.MEMBERSHIP_KEY_MEMBER));
        String role = isMember ? (String) membership.get(BusinessConstants.MEMBERSHIP_KEY_ROLE) : null;

        Map<String, Object> result = new LinkedHashMap<>(16);
        result.put("gid", group.getGid());
        result.put("name", group.getName());
        result.put("avatar", group.getAvatar());
        result.put("ownerUid", group.getOwnerUid());
        result.put("announcement", group.getAnnouncement());
        result.put("isMember", isMember);
        result.put("memberRole", role);
        return Result.ok(result);
    }

    /**
     * 更新群组属性，如名称、头像、公告或慢速模式间隔。
     *
     * @param gid  群组ID
     * @param body 字段名到新值的映射
     * @return 更新后的群组信息
     */
    @PutMapping("/{gid:[0-9]+}")
    public Result<GroupInfo> updateGroup(@PathVariable Long gid, @RequestBody Map<String, Object> body) {
        Long uid = UserContext.get();
        return Result.ok(groupService.updateGroup(uid, gid, body));
    }

    /**
     * 解散（删除）群组。仅所有者可解散。
     *
     * @param gid 群组ID
     * @return 成功时返回空结果
     */
    @DeleteMapping("/{gid:[0-9]+}")
    public Result<Void> dissolveGroup(@PathVariable Long gid) {
        Long uid = UserContext.get();
        groupService.dissolveGroup(uid, gid);
        return Result.ok();
    }

    /**
     * 将群组所有权转让给另一个成员。
     *
     * @param gid 群组ID
     * @param req 包含 "newOwnerUid" 的请求
     * @return 成功时返回空结果
     */
    @PutMapping("/{gid:[0-9]+}/transfer")
    public Result<Void> transferOwner(@PathVariable Long gid, @Valid @RequestBody TransferOwnerRequest req) {
        Long uid = UserContext.get();
        groupService.transferOwner(uid, gid, req.getNewOwnerUid());
        return Result.ok();
    }

    /**
     * 列出当前用户拥有的群组。
     *
     * @return 拥有的群组列表
     */
    @GetMapping("/owned")
    public Result<List<GroupInfo>> getOwnedGroups() {
        Long uid = UserContext.get();
        return Result.ok(groupService.getOwnedGroups(uid));
    }

    /**
     * 列出当前用户已加入的群组。
     *
     * @return 已加入的群组列表
     */
    @GetMapping("/joined")
    public Result<List<GroupInfo>> getJoinedGroups() {
        Long uid = UserContext.get();
        return Result.ok(groupService.getJoinedGroups(uid));
    }

    /**
     * 获取群组的所有成员。
     *
     * @param gid 群组ID
     * @return 成员信息map列表
     */
    @GetMapping("/{gid:[0-9]+}/members")
    public Result<List<Map<String, Object>>> getMembers(@PathVariable Long gid) {
        Long uid = UserContext.get();
        Map<String, Object> membership = memberService.checkMembership(gid, uid);
        if (membership == null || !Boolean.TRUE.equals(membership.get(BusinessConstants.MEMBERSHIP_KEY_MEMBER))) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Not a member of this group");
        }
        return Result.ok(memberService.getMembers(gid));
    }

    /**
     * 邀请用户加入群组。仅所有者和管理员可以邀请。
     *
     * @param gid 群组ID
     * @param req 包含 "uids" 列表的请求
     * @return 成功时返回空结果
     */
    @PostMapping("/{gid:[0-9]+}/members")
    public Result<Void> inviteMembers(@PathVariable Long gid, @Valid @RequestBody InviteMembersRequest req) {
        Long uid = UserContext.get();
        memberService.inviteMembers(uid, gid, req.getUids());
        return Result.ok();
    }

    /**
     * 将成员踢出群组。需要管理权限。
     *
     * @param gid       群组ID
     * @param targetUid 要踢出的成员UID
     * @return 成功时返回空结果
     */
    @DeleteMapping("/{gid:[0-9]+}/members/{targetUid}")
    public Result<Void> kickMember(@PathVariable Long gid, @PathVariable Long targetUid) {
        Long uid = UserContext.get();
        memberService.kickMember(uid, gid, targetUid);
        return Result.ok();
    }

    /**
     * 退出群组。所有者需先转让所有权。
     *
     * @param gid 群组ID
     * @return 成功时返回空结果
     */
    @DeleteMapping("/{gid:[0-9]+}/leave")
    public Result<Void> leaveGroup(@PathVariable Long gid) {
        Long uid = UserContext.get();
        memberService.leaveGroup(uid, gid);
        return Result.ok();
    }

    /**
     * 设置成员角色。仅所有者可以修改角色。
     *
     * @param gid       群组ID
     * @param targetUid 目标成员UID
     * @param req       包含 "role" 的请求
     * @return 成功时返回空结果
     */
    @PutMapping("/{gid:[0-9]+}/members/{targetUid}/role")
    public Result<Void> setRole(@PathVariable Long gid, @PathVariable Long targetUid,
                                 @Valid @RequestBody SetRoleRequest req) {
        Long uid = UserContext.get();
        memberService.setRole(uid, gid, targetUid, req.getRole());
        return Result.ok();
    }

    /**
     * 对成员禁言指定时长。需要管理权限。
     *
     * @param gid       群组ID
     * @param targetUid 目标成员UID
     * @param req       包含 "minutes"（默认10）的请求
     * @return 成功时返回空结果
     */
    @PostMapping("/{gid:[0-9]+}/members/{targetUid}/mute")
    public Result<Void> muteMember(@PathVariable Long gid, @PathVariable Long targetUid,
                                    @Valid @RequestBody MuteMemberRequest req) {
        Long uid = UserContext.get();
        int minutes = req.getMinutes() != null ? req.getMinutes() : 10;
        memberService.muteMember(uid, gid, targetUid, minutes);
        return Result.ok();
    }

    /**
     * 解除成员禁言。
     *
     * @param gid       群组ID
     * @param targetUid 目标成员UID
     * @return 成功时返回空结果
     */
    @DeleteMapping("/{gid:[0-9]+}/members/{targetUid}/mute")
    public Result<Void> unmuteMember(@PathVariable Long gid, @PathVariable Long targetUid) {
        Long uid = UserContext.get();
        memberService.unmuteMember(uid, gid, targetUid);
        return Result.ok();
    }

    @PutMapping("/{gid:[0-9]+}/mute-all")
    public Result<Void> muteAll(@PathVariable Long gid) {
        Long uid = UserContext.get();
        groupService.muteAll(uid, gid, true);
        return Result.ok();
    }

    @DeleteMapping("/{gid:[0-9]+}/mute-all")
    public Result<Void> unmuteAll(@PathVariable Long gid) {
        Long uid = UserContext.get();
        groupService.muteAll(uid, gid, false);
        return Result.ok();
    }

    /**
     * 为群组创建邀请链接。
     *
     * @param gid 群组ID
     * @param req 包含 "expireHours"（默认24）的请求
     * @return 包含邀请码和过期时间的map
     */
    @PostMapping("/{gid:[0-9]+}/invite")
    public Result<Map<String, Object>> createInvite(@PathVariable Long gid,
                                                      @Valid @RequestBody CreateInviteRequest req) {
        Long uid = UserContext.get();
        int expireHours = req.getExpireHours() != null ? req.getExpireHours() : 24;
        return Result.ok(inviteService.createInvite(uid, gid, expireHours));
    }

    /**
     * 根据邀请码获取邀请链接详情。
     *
     * @param code 邀请码
     * @return 包含群组信息和过期状态的map
     */
    @GetMapping("/invite/{code}")
    public Result<Map<String, Object>> getInviteInfo(@PathVariable String code) {
        UserContext.get();
        return Result.ok(inviteService.getInviteInfo(code));
    }

    /**
     * 使用邀请码加入群组。
     *
     * @param code 邀请码
     * @return 成功时返回空结果
     */
    @PostMapping("/join/{code}")
    public Result<Void> joinByInvite(@PathVariable String code) {
        Long uid = UserContext.get();
        memberService.joinByInvite(uid, code);
        return Result.ok();
    }

    @PostMapping("/{gid:[0-9]+}/join-request")
    public Result<GroupJoinRequest> applyToJoin(@PathVariable Long gid, @Valid @RequestBody ApplyJoinRequest req) {
        Long uid = UserContext.get();
        String message = req.getMessage() != null ? req.getMessage() : "";
        return Result.ok(joinRequestService.apply(uid, gid, message));
    }

    @GetMapping("/{gid:[0-9]+}/join-requests")
    public Result<List<GroupJoinRequest>> getPendingRequests(@PathVariable Long gid) {
        Long uid = UserContext.get();
        GroupMember member = memberService.getMember(gid, uid);
        if (member == null || (!BusinessConstants.ROLE_OWNER.equals(member.getRole()) && !BusinessConstants.ROLE_ADMIN.equals(member.getRole()))) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Only owner and admins can view pending requests");
        }
        return Result.ok(joinRequestService.getPendingRequests(gid));
    }

    @PutMapping("/join-requests/{id}/approve")
    public Result<GroupJoinRequest> approveRequest(@PathVariable Long id) {
        Long uid = UserContext.get();
        return Result.ok(joinRequestService.approve(uid, id));
    }

    @PutMapping("/join-requests/{id}/reject")
    public Result<GroupJoinRequest> rejectRequest(@PathVariable Long id) {
        Long uid = UserContext.get();
        return Result.ok(joinRequestService.reject(uid, id));
    }

    @GetMapping("/{gid:[0-9]+}/join-request/my")
    public Result<GroupJoinRequest> getMyRequest(@PathVariable Long gid) {
        Long uid = UserContext.get();
        GroupJoinRequest req = joinRequestService.getMyRequest(uid, gid);
        if (req == null) {
            return Result.ok();
        }
        return Result.ok(req);
    }
}
