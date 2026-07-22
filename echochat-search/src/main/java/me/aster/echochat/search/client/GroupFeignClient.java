package me.aster.echochat.search.client;

import me.aster.echochat.search.entity.GroupDocument;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * echochat-group服务的Feign客户端，用于全局搜索功能中按关键词搜索群组。
 * @author AsterWinston
 */
@FeignClient(name = "echochat-group", path = "/internal/group")
public interface GroupFeignClient {

    /**
     * 根据关键词搜索群组。
     *
     * @param keyword 搜索关键词
     * @return 匹配的群组文档列表
     */
    @GetMapping("/search")
    List<GroupDocument> searchGroups(@RequestParam("keyword") String keyword);

    /**
     * 批量查询群组信息。
     *
     * @param gids 群组ID列表
     * @return gid到群组信息map的映射
     */
    @PostMapping("/info/batch")
    Map<String, Map<String, Object>> getGroupInfoBatch(@RequestBody List<Long> gids);
}
