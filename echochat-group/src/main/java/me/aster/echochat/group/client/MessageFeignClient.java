package me.aster.echochat.group.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.Map;

/**
 * echochat-message服务的Feign客户端，用于代表群组模块向用户或群组发送系统消息。
 * @author AsterWinston
 */
@FeignClient(name = "echochat-message", path = "/internal/message")
public interface MessageFeignClient {

    /**
     * 向用户或群组发送系统消息。
     *
     * @param body 包含gid、fromUid、content等字段的消息内容
     */
    @PostMapping("/system")
    void sendSystemMessage(@RequestBody Map<String, Object> body);
}
