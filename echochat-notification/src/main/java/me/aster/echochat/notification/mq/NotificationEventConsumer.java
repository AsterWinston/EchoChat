package me.aster.echochat.notification.mq;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.common.dto.NotificationEvent;
import me.aster.echochat.notification.service.NotificationService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 消费{@code notification-topic}中的{@link NotificationEvent}，
 * 持久化并通过事件ID幂等推送。持久化失败会向上传播以便RocketMQ重新投递事件；
 * 格式错误的消息会被跳过，避免毒消息重试循环。
 * @author AsterWinston
 */
@Slf4j
@Component
@RocketMQMessageListener(topic = "notification-topic", consumerGroup = "${rocketmq.consumer.group}")
public class NotificationEventConsumer implements RocketMQListener<String> {

    private final NotificationService notificationService;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public NotificationEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * @param eventJson 通知事件的JSON表示
     */
    @Override
    public void onMessage(String eventJson) {
        NotificationEvent event;
        try {
            event = OBJECT_MAPPER.readValue(eventJson, NotificationEvent.class);
        } catch (Exception e) {
            log.error("Skipping malformed notification event: {}", eventJson, e);
            return;
        }
        if (event.getUid() == null || event.getEventId() == null) {
            log.error("Skipping notification event without uid/eventId: {}", eventJson);
            return;
        }
        notificationService.createFromEvent(event);
    }
}
