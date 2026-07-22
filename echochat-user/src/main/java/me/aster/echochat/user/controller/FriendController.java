package me.aster.echochat.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.aster.echochat.common.context.UserContext;
import me.aster.echochat.common.result.Result;
import me.aster.echochat.user.dto.SendFriendRequest;
import me.aster.echochat.user.dto.UpdateFriendRequest;
import me.aster.echochat.user.entity.FriendRequest;
import me.aster.echochat.user.service.FriendService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 好友和黑名单管理的 REST 控制器。
 * 处理好友请求、好友列表操作和黑名单的增删查。
 * @author AsterWinston
 */
@RestController
@RequestMapping("/api/friend")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    /**
     * 当前用户向目标用户发送好友请求。
     *
     * @param req 包含 toUid 和可选 message 的请求
     * @return 成功结果
     */
    @PostMapping("/request")
    public Result<Void> sendRequest(@Valid @RequestBody SendFriendRequest req) {
        Long uid = UserContext.get();
        String message = req.getMessage() != null ? req.getMessage() : "";
        friendService.sendRequest(uid, req.getToUid(), message);
        return Result.ok();
    }

    /**
     * 根据请求 ID 接受待处理的好友请求。
     *
     * @param id 好友请求 ID
     * @return 成功结果
     */
    @PutMapping("/request/{id}/accept")
    public Result<Void> acceptRequest(@PathVariable Long id) {
        Long uid = UserContext.get();
        friendService.acceptRequest(id, uid);
        return Result.ok();
    }

    /**
     * 根据请求 ID 拒绝待处理的好友请求。
     *
     * @param id 好友请求 ID
     * @return 成功结果
     */
    @PutMapping("/request/{id}/reject")
    public Result<Void> rejectRequest(@PathVariable Long id) {
        Long uid = UserContext.get();
        friendService.rejectRequest(id, uid);
        return Result.ok();
    }

    /**
     * 获取当前用户收到的好友请求，可按状态过滤。
     *
     * @param status 可选的状态过滤（例如 "pending", "accepted", "rejected"）
     * @return 收到的好友请求列表
     */
    @GetMapping("/requests/received")
    public Result<List<FriendRequest>> getReceivedRequests(@RequestParam(required = false) String status) {
        Long uid = UserContext.get();
        return Result.ok(friendService.getReceivedRequests(uid, status));
    }

    /**
     * 获取当前用户发出的好友请求，可按状态过滤。
     *
     * @param status 可选的状态过滤（例如 "pending", "accepted", "rejected"）
     * @return 发出的好友请求列表
     */
    @GetMapping("/requests/sent")
    public Result<List<FriendRequest>> getSentRequests(@RequestParam(required = false) String status) {
        Long uid = UserContext.get();
        return Result.ok(friendService.getSentRequests(uid, status));
    }

    /**
     * 获取当前用户的好友列表及个人资料信息。
     *
     * @return 包含昵称、头像、分组等信息的好友列表
     */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> getFriendList() {
        Long uid = UserContext.get();
        return Result.ok(friendService.getFriendList(uid));
    }

    /**
     * 更新好友的分组/类别或备注。
     *
     * @param friendUid 好友的 UID
     * @param req       包含 groupName 和/或 memo 的请求
     * @return 成功结果
     */
    @PutMapping("/{friendUid}")
    public Result<Void> updateGroup(@PathVariable Long friendUid, @Valid @RequestBody UpdateFriendRequest req) {
        Long uid = UserContext.get();
        friendService.updateGroup(uid, friendUid, req.getGroupName(), req.getMemo());
        return Result.ok();
    }

    /**
     * 删除当前用户的好友关系。
     *
     * @param friendUid 要删除的好友 UID
     * @return 成功结果
     */
    @DeleteMapping("/{friendUid}")
    public Result<Void> deleteFriend(@PathVariable Long friendUid) {
        Long uid = UserContext.get();
        friendService.deleteFriend(uid, friendUid);
        return Result.ok();
    }

    /**
     * 将用户添加到当前用户的黑名单。
     *
     * @param blockedUid 要屏蔽的用户 UID
     * @return 成功结果
     */
    @PostMapping("/blacklist/{blockedUid}")
    public Result<Void> addToBlacklist(@PathVariable Long blockedUid) {
        Long uid = UserContext.get();
        friendService.addToBlacklist(uid, blockedUid);
        return Result.ok();
    }

    /**
     * 将用户从当前用户的黑名单中移除。
     *
     * @param blockedUid 要解除屏蔽的用户 UID
     * @return 成功结果
     */
    @DeleteMapping("/blacklist/{blockedUid}")
    public Result<Void> removeFromBlacklist(@PathVariable Long blockedUid) {
        Long uid = UserContext.get();
        friendService.removeFromBlacklist(uid, blockedUid);
        return Result.ok();
    }

    /**
     * 获取当前用户的黑名单，包含基本的用户信息。
     *
     * @return 黑名单用户列表
     */
    @GetMapping("/blacklist")
    public Result<List<Map<String, Object>>> getBlacklist() {
        Long uid = UserContext.get();
        return Result.ok(friendService.getBlacklist(uid));
    }
}
