package me.aster.echochat.notification.controller;

import lombok.RequiredArgsConstructor;
import me.aster.echochat.common.context.UserContext;
import me.aster.echochat.common.result.Result;
import me.aster.echochat.notification.entity.Notification;
import me.aster.echochat.notification.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 面向用户的通知操作REST控制器。
 * 从{@link UserContext}解析当前用户，并委托给{@link NotificationService}处理。
 * @author AsterWinston
 */
@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 列出当前用户的通知。
     *
     * @param limit 最多返回的通知数（默认50）
     * @return 包含通知列表的结果
     */
    @GetMapping("/list")
    public Result<List<Notification>> getNotifications(@RequestParam(defaultValue = "50") int limit) {
        Long uid = UserContext.get();
        return Result.ok(notificationService.getNotifications(uid, limit));
    }

    /**
     * 返回当前用户的未读通知数量。
     *
     * @return 包含计数的Map结果
     */
    @GetMapping("/unread-count")
    public Result<Map<String, Object>> getUnreadCount() {
        Long uid = UserContext.get();
        return Result.ok(Map.of("count", notificationService.getUnreadCount(uid)));
    }

    /**
     * 将单条通知标记为已读，含归属验证。
     *
     * @param id 通知ID
     * @return 空成功结果
     */
    @PutMapping("/read/{id}")
    public Result<Void> markAsRead(@PathVariable Long id) {
        Long uid = UserContext.get();
        notificationService.markAsRead(uid, id);
        return Result.ok();
    }

    /**
     * 将当前用户的所有未读通知标记为已读。
     *
     * @return 空成功结果
     */
    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        Long uid = UserContext.get();
        notificationService.markAllRead(uid);
        return Result.ok();
    }
}
