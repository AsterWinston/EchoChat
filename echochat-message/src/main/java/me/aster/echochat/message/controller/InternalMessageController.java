package me.aster.echochat.message.controller;

import lombok.RequiredArgsConstructor;
import me.aster.echochat.message.service.MessageService;
import me.aster.echochat.message.mapper.MessageDeletionMapper;
import me.aster.echochat.message.entity.MessageDeletion;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import me.aster.echochat.message.websocket.WebSocketPushService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 内部REST控制器，通过Feign为WebSocket推送通知提供端点。
 * 允许其他微服务向已连接的WebSocket客户端推送实时通知载荷。
 * @author AsterWinston
 */
@RestController
@RequestMapping("/internal/message")
@RequiredArgsConstructor
public class InternalMessageController {

    private final WebSocketPushService pushService;
    private final MessageService messageService;
    private final MessageDeletionMapper messageDeletionMapper;

    /**
     * 向目标用户的WebSocket连接推送通知载荷。
     *
     * @param uid     目标用户ID
     * @param payload 要推送的通知数据
     * @return 成功响应map
     */
    @PostMapping("/push-notification/{uid}")
    public Map<String, Object> pushNotification(@PathVariable Long uid, @RequestBody Map<String, Object> payload) {
        pushService.pushNotification(uid, payload);
        return Map.of("success", true);
    }

    @PostMapping("/system")
    public Map<String, Object> sendSystemMessage(@RequestBody Map<String, Object> body) {
        Long gid = Long.parseLong(String.valueOf(body.get("gid")));
        Long fromUid = Long.parseLong(String.valueOf(body.get("fromUid")));
        String content = (String) body.get("content");
        messageService.sendSystemGroupMessage(gid, fromUid, content);
        return Map.of("success", true);
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/deleted-msg-ids")
    public List<Long> getDeletedMsgIds(@RequestBody Map<String, Object> body) {
        Long uid = Long.parseLong(String.valueOf(body.get("uid")));
        List<Number> msgIdNums = (List<Number>) body.get("msgIds");
        if (msgIdNums == null || msgIdNums.isEmpty()) {
            return List.of();
        }
        List<Long> msgIds = msgIdNums.stream().map(Number::longValue).toList();
        return messageDeletionMapper.selectList(
            new LambdaQueryWrapper<MessageDeletion>()
                .eq(MessageDeletion::getUid, uid)
                .in(MessageDeletion::getMsgId, msgIds))
            .stream().map(MessageDeletion::getMsgId).collect(Collectors.toList());
    }
}