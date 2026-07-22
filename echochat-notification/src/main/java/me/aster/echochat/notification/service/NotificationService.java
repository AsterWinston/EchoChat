package me.aster.echochat.notification.service;

import me.aster.echochat.common.exception.BusinessException;
import me.aster.echochat.notification.entity.Notification;

import java.util.List;

/**
 * 通知操作的服务接口。
 * @author AsterWinston
 */
public interface NotificationService {

    /**
     * 创建通知，持久化存储并尝试通过WebSocket推送。
     * 推送失败仅记录警告日志，不会阻止通知创建。
     *
     * @param uid       目标用户ID
     * @param type      通知类型
     * @param title     通知标题
     * @param content   通知内容
     * @param relatedId 关联资源ID（可选）
     * @return 创建的通知
     */
    Notification createNotification(Long uid, String type, String title, String content, Long relatedId);

    /**
     * 从RocketMQ事件创建通知，基于事件ID去重，
     * 确保重复投递的事件不会产生重复通知。
     *
     * @param event 从主题消费的通知事件
     */
    void createFromEvent(me.aster.echochat.common.dto.NotificationEvent event);

    /**
     * 获取用户的近期通知。
     *
     * @param uid   目标用户ID
     * @param limit 最多返回的通知数
     * @return 按创建时间倒序排列的通知列表
     */
    List<Notification> getNotifications(Long uid, int limit);

    /**
     * 返回用户的未读通知数量。
     *
     * @param uid 目标用户ID
     * @return 未读数量
     */
    int getUnreadCount(Long uid);

    /**
     * 验证归属后将单条通知标记为已读。
     *
     * @param uid            请求用户ID
     * @param notificationId 要标记的通知ID
     * @throws BusinessException 通知不存在（NOTIFICATION_NOT_FOUND）
     *                           或通知不属于该用户（NOTIFICATION_PERMISSION_DENIED）时抛出
     */
    void markAsRead(Long uid, Long notificationId);

    /**
     * 将用户的所有未读通知标记为已读。
     *
     * @param uid 目标用户ID
     */
    void markAllRead(Long uid);
}
