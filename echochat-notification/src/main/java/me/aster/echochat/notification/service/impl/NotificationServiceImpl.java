package me.aster.echochat.notification.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.common.exception.BusinessException;
import me.aster.echochat.common.result.ResultCode;
import me.aster.echochat.common.util.SnowflakeIdGenerator;
import me.aster.echochat.notification.client.MessageFeignClient;
import me.aster.echochat.notification.entity.Notification;
import me.aster.echochat.notification.mapper.NotificationMapper;
import me.aster.echochat.notification.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link NotificationService}的实现。
 * 处理通知创建（雪花ID生成、持久化、WebSocket推送）、
 * 通知获取、未读计数以及带归属验证的已读状态更新。
 * @author AsterWinston
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final MessageFeignClient messageFeignClient;

    /**
     * 使用雪花算法生成ID创建通知，持久化存储，
     * 并尝试通过WebSocket推送给目标用户。
     * WebSocket推送失败会记录日志但不会阻止通知创建。
     *
     * @param uid       目标用户ID
     * @param type      通知类型
     * @param title     通知标题
     * @param content   通知内容
     * @param relatedId 关联资源ID（可选）
     * @return 创建的通知
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Notification createNotification(Long uid, String type, String title, String content, Long relatedId) {
        Notification notification = new Notification();
        notification.setId(idGenerator.nextId());
        notification.setUid(uid);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setRelatedId(relatedId);
        notification.setIsRead(0);
        notification.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(notification);

        try {
            Map<String, Object> payload = new HashMap<>(16);
            payload.put("id", notification.getId());
            payload.put("type", type);
            payload.put("title", title);
            payload.put("content", content);
            payload.put("createdAt", notification.getCreatedAt().toString());
            messageFeignClient.pushNotification(uid, payload);
        } catch (Exception e) {
            log.warn("WS push failed for notification id={}, uid={}", notification.getId(), uid);
        }

        log.info("Notification created: id={}, uid={}, type={}", notification.getId(), uid, type);
        return notification;
    }

    /**
     * 从RocketMQ事件创建通知，基于事件ID去重：
     * 重复投递会命中{@code event_id}唯一索引并被跳过。
     *
     * @param event 从主题消费的通知事件
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createFromEvent(me.aster.echochat.common.dto.NotificationEvent event) {
        Notification notification = new Notification();
        notification.setId(idGenerator.nextId());
        notification.setUid(event.getUid());
        notification.setType(event.getType());
        notification.setTitle(event.getTitle());
        notification.setContent(event.getContent());
        notification.setRelatedId(event.getRelatedId());
        notification.setEventId(event.getEventId());
        notification.setIsRead(0);
        notification.setCreatedAt(LocalDateTime.now());
        try {
            notificationMapper.insert(notification);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            log.info("Duplicate notification event skipped: eventId={}", event.getEventId());
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>(16);
            payload.put("id", notification.getId());
            payload.put("type", notification.getType());
            payload.put("title", notification.getTitle());
            payload.put("content", notification.getContent());
            payload.put("createdAt", notification.getCreatedAt().toString());
            messageFeignClient.pushNotification(event.getUid(), payload);
        } catch (Exception e) {
            log.warn("WS push failed for notification id={}, uid={}", notification.getId(), event.getUid());
        }

        log.info("Notification created from event: id={}, eventId={}, uid={}, type={}",
                notification.getId(), event.getEventId(), event.getUid(), event.getType());
    }

    @Override
    public List<Notification> getNotifications(Long uid, int limit) {
        return notificationMapper.findByUid(uid, limit);
    }

    @Override
    public int getUnreadCount(Long uid) {
        return notificationMapper.countUnread(uid);
    }

    /**
     * 验证以下条件后将单条通知标记为已读：
     * <ul>
     *   <li>通知存在</li>
     *   <li>通知属于请求用户</li>
     * </ul>
     *
     * @param uid            请求用户ID
     * @param notificationId 要标记的通知ID
     * @throws BusinessException 通知不存在或不属于该用户时抛出
     */
    @Override
    public void markAsRead(Long uid, Long notificationId) {
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification == null) {
            throw new BusinessException(ResultCode.NOTIFICATION_NOT_FOUND);
        }
        if (!notification.getUid().equals(uid)) {
            throw new BusinessException(ResultCode.NOTIFICATION_PERMISSION_DENIED);
        }
        if (notification.getIsRead() == 1) {
            return;
        }
        notification.setIsRead(1);
        notificationMapper.updateById(notification);
    }

    @Override
    public void markAllRead(Long uid) {
        notificationMapper.markAllRead(uid);
    }
}
