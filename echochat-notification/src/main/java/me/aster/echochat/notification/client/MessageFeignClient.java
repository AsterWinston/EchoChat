package me.aster.echochat.notification.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * echochat-message服务的Feign客户端。
 * 用于通过WebSocket向用户推送实时通知。
 * @author AsterWinston
 */
@FeignClient(name = "echochat-message", path = "/internal/message")
public interface MessageFeignClient {

    /**
     * 通过WebSocket向指定用户推送通知数据。
     *
     * @param uid     目标用户ID
     * @param payload 通知数据Map（id、type、title、content、createdAt）
     * @return 消息服务的响应Map
     */
    @PostMapping("/push-notification/{uid}")
    Map<String, Object> pushNotification(@PathVariable Long uid, @RequestBody Map<String, Object> payload);
}
