package me.aster.echochat.search.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.search.entity.MessageDocument;
import me.aster.echochat.search.repository.MessageSearchRepository;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * RocketMQ消费者，监听{@code message-topic}上的新聊天消息并将其索引到Elasticsearch。
 * @author AsterWinston
 */
@Slf4j
@Component
@RocketMQMessageListener(topic = "message-topic", consumerGroup = "${rocketmq.consumer.group}")
public class MessageConsumer implements RocketMQListener<String> {

    private final MessageSearchRepository messageSearchRepository;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public MessageConsumer(MessageSearchRepository messageSearchRepository) {
        this.messageSearchRepository = messageSearchRepository;
    }

    /**
     * 将接收到的JSON消息字符串反序列化为{@link MessageDocument}，
     * 计算会话ID，并保存到Elasticsearch。
     *
     * @param messageJson 聊天消息的JSON表示
     */
    @Override
    public void onMessage(String messageJson) {
        MessageDocument doc;
        try {
            doc = OBJECT_MAPPER.readValue(messageJson, MessageDocument.class);
        } catch (Exception e) {
            // 不可重试：消息格式错误。记录日志并跳过，避免毒消息重试循环。
            log.error("Skipping malformed message from RocketMQ: {}", messageJson, e);
            return;
        }
        doc.setSessionId(doc.getSessionType() + "_" + doc.getToId());
        // 让索引失败（如ES不可用）向上传播，以便RocketMQ重新投递。
        messageSearchRepository.save(doc);
        log.info("Message indexed to ES: msgId={}", doc.getMsgId());
    }
}
