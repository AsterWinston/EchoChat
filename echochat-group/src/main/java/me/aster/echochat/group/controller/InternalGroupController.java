package me.aster.echochat.group.controller;

import lombok.RequiredArgsConstructor;
import me.aster.echochat.group.entity.GroupInfo;
import me.aster.echochat.group.entity.GroupMember;
import me.aster.echochat.group.service.GroupMemberService;
import me.aster.echochat.group.service.GroupService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内部控制器，用于跨服务群组成员查询。供其他微服务通过Feign调用使用。
 * @author AsterWinston
 */
@RestController
@RequestMapping("/internal/group")
@RequiredArgsConstructor
public class InternalGroupController {

    private final GroupMemberService memberService;
    private final GroupService groupService;

    /**
     * @param gid 群组ID
     * @return 包含群组名称和头像的map
     */
    @GetMapping("/{gid}/info")
    public Map<String, Object> getGroupInfo(@PathVariable Long gid) {
        GroupInfo group = groupService.getGroupInfo(gid);
        Map<String, Object> result = new LinkedHashMap<>(16);
        result.put("gid", group.getGid());
        result.put("name", group.getName());
        result.put("avatar", group.getAvatar());
        return result;
    }

    /**
     * 单次查询批量获取群组信息；供会话列表使用以避免N+1次Feign调用。
     *
     * @param gids 要获取的群组ID列表
     * @return gid（字符串格式）到 {gid, name, avatar} 的映射；不存在的群组被省略
     */
    @PostMapping("/info/batch")
    public Map<String, Map<String, Object>> getGroupInfoBatch(@RequestBody List<Long> gids) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>(16);
        for (GroupInfo group : groupService.getGroupInfos(gids)) {
            Map<String, Object> info = new LinkedHashMap<>(16);
            info.put("gid", group.getGid());
            info.put("name", group.getName());
            info.put("avatar", group.getAvatar());
            result.put(String.valueOf(group.getGid()), info);
        }
        return result;
    }

    /**
     * @param gid 群组ID
     * @return 群组中的成员UID列表
     */
    @GetMapping("/{gid}/members")
    public List<Long> getMemberUids(@PathVariable Long gid) {
        return memberService.getMemberUids(gid);
    }

    /**
     * @param gid 群组ID
     * @param uid 用户ID
     * @return 包含成员状态、角色和禁言状态的map
     */
    @GetMapping("/{gid}/member/{uid}")
    public Map<String, Object> checkMembership(@PathVariable Long gid, @PathVariable Long uid) {
        return memberService.checkMembership(gid, uid);
    }

    /**
     * @param gid 群组ID
     * @param uid 用户ID
     * @return {@link GroupMember} 实体，非成员则返回null
     */
    @GetMapping("/{gid}/member/{uid}/detail")
    public GroupMember getMember(@PathVariable Long gid, @PathVariable Long uid) {
        return memberService.getMember(gid, uid);
    }

    /**
     * @param gid 群组ID
     * @return 以 "count" 为键的成员数量map
     */
    @GetMapping("/{gid}/count")
    public Map<String, Integer> getMemberCount(@PathVariable Long gid) {
        return Map.of("count", memberService.getMemberCount(gid));
    }

    @GetMapping("/search")
    public List<GroupInfo> searchGroups(@RequestParam String keyword) {
        return groupService.searchGroups(keyword);
    }
}
