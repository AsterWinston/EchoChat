package me.aster.echochat.group.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.common.dto.NotificationEvent;
import me.aster.echochat.common.util.SnowflakeIdGenerator;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 在周围业务事务提交后将 {@link NotificationEvent} 发布到 {@code notification-topic}，
 * 替代了旧的同步Feign调用（其失败会静默丢弃通知）。交付可靠性来自消费者端的RocketMQ重试；
 * 事务提交后发送失败会以ERROR级别记录日志（概率极小，业务数据绝不会因通知而回滚）。
 * @author AsterWinston
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventPublisher {

    private final RocketMQTemplate rocketMQTemplate;
    private final SnowflakeIdGenerator idGenerator;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TOPIC = "notification-topic";

    /**
     * 在当前事务提交后发布通知事件（无活跃事务时立即发送）。
     *
     * @param uid       接收者用户ID
     * @param type      通知类型
     * @param title     通知标题
     * @param content   通知内容
     * @param relatedId 关联业务实体ID
     */
    public void publishAfterCommit(Long uid, String type, String title, String content, Long relatedId) {
        NotificationEvent event = NotificationEvent.builder()
                .eventId(idGenerator.nextId())
                .uid(uid)
                .type(type)
                .title(title)
                .content(content)
                .relatedId(relatedId)
                .build();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(event);
                }
            });
        } else {
            send(event);
        }
    }

    /**
     * 序列化并发送事件；失败仅记录日志，绝不向上传播。
     *
     * @param event 通知事件
     */
    private void send(NotificationEvent event) {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                rocketMQTemplate.syncSend(TOPIC, OBJECT_MAPPER.writeValueAsString(event), 3000);
                log.debug("Notification event published: eventId={}, uid={}, type={}",
                        event.getEventId(), event.getUid(), event.getType());
                return;
            } catch (Exception e) {
                if (i == maxRetries - 1) {
                    log.error("Notification event send failed after {} retries, event dropped: eventId={}, uid={}, type={}",
                            maxRetries, event.getEventId(), event.getUid(), event.getType(), e);
                } else {
                    log.warn("Notification event send retry {}/{}: eventId={}, uid={}, type={}",
                            i + 1, maxRetries, event.getEventId(), event.getUid(), event.getType());
                    try { Thread.sleep(100L * (i + 1)); } catch (InterruptedException ignored) {}
                }
            }
        }
    }
}
