package me.aster.echochat.search.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

/**
 * echochat-message服务的Feign客户端，用于获取已删除消息的ID列表，
 * 以便在搜索结果中过滤掉用户已删除的消息。
 * @author AsterWinston
 */
@FeignClient(name = "echochat-message", path = "/internal/message")
public interface MessageFeignClient {

    /**
     * 获取已删除消息的ID列表，用于过滤搜索结果。
     *
     * @param body 包含查询条件的参数map
     * @return 已删除消息的ID列表
     */
    @PostMapping("/deleted-msg-ids")
    List<Long> getDeletedMsgIds(@RequestBody Map<String, Object> body);
}
