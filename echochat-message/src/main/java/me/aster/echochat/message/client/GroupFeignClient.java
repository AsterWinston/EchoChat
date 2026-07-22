package me.aster.echochat.message.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

/**
 * echochat-group服务的Feign客户端，用于验证群成员身份和查询成员列表。
 * @author AsterWinston
 */
@FeignClient(name = "echochat-group", path = "/internal/group")
public interface GroupFeignClient {

    /**
     * 获取群组基本信息。
     *
     * @param gid 群组ID
     * @return 包含群名和头像的群组信息
     */
    @GetMapping("/{gid}/info")
    Map<String, Object> getGroupInfo(@PathVariable Long gid);

    /**
     * 批量获取群组信息，一次调用完成。
     *
     * @param gids 群组ID列表
     * @return gid（字符串）到{gid, name, avatar}的映射；不存在的群组不包含
     */
    @PostMapping("/info/batch")
    Map<String, Map<String, Object>> getGroupInfoBatch(@RequestBody List<Long> gids);

    /**
     * 检查用户是否为群组成员并获取成员详情。
     *
     * @param gid 群组ID
     * @param uid 要检查的用户ID
     * @return 包含成员详情（含静音状态）的map
     */
    @GetMapping("/{gid}/member/{uid}")
    Map<String, Object> checkMembership(@PathVariable Long gid, @PathVariable Long uid);

    /**
     * 获取群组所有成员的用户ID列表。
     *
     * @param gid 群组ID
     * @return 群组中所有成员的用户ID列表
     */
    @GetMapping("/{gid}/members")
    List<Long> getMemberUids(@PathVariable Long gid);

    /**
     * 获取群组成员数量。
     *
     * @param gid 群组ID
     * @return 键为"count"的成员数量map
     */
    @GetMapping("/{gid}/count")
    Map<String, Integer> getMemberCount(@PathVariable Long gid);
}