package me.aster.echochat.notification.controller;

import lombok.RequiredArgsConstructor;
import me.aster.echochat.notification.entity.Notification;
import me.aster.echochat.notification.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 服务间通知创建的内部REST控制器。
 * 供其他微服务调用以创建通知，无需经过公开API。
 * @author AsterWinston
 */
@RestController
@RequestMapping("/internal/notification")
@RequiredArgsConstructor
public class InternalNotificationController {

    private final NotificationService notificationService;

    /**
     * 从自由格式的Map请求体创建通知（供Feign调用方使用）。
     *
     * @param body 包含uid、type、title、可选content（默认空字符串）和可选relatedId的Map
     * @return 创建的{@link Notification}
     * @throws IllegalArgumentException uid为null，或type/title为null或空白时抛出
     * @throws NumberFormatException    uid无法解析为数字时抛出
     */
    @PostMapping
    public Notification createNotification(@RequestBody Map<String, Object> body) {
        Object uidObj = body.get("uid");
        if (uidObj == null) {
            throw new IllegalArgumentException("uid is required");
        }
        Long uid = uidObj instanceof Number ? ((Number) uidObj).longValue() : Long.parseLong(uidObj.toString());
        String type = (String) body.get("type");
        String title = (String) body.get("title");
        if (type == null || type.isBlank() || title == null || title.isBlank()) {
            throw new IllegalArgumentException("type and title are required");
        }
        String content = (String) body.getOrDefault("content", "");
        Object relatedObj = body.get("relatedId");
        Long relatedId = relatedObj != null ? (relatedObj instanceof Number ? ((Number) relatedObj).longValue() : Long.parseLong(relatedObj.toString())) : null;
        return notificationService.createNotification(uid, type, title, content, relatedId);
    }
}
