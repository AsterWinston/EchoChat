package me.aster.echochat.message.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.message.service.impl.MessageTxService;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

/**
 * 将聊天消息持久化到MySQL，然后将索引事件发布到RocketMQ
 * 用于异步Elasticsearch写入。当消息队列不可用时消息仍会持久化（聊天保持可用）；
 * 搜索索引会错过该事件，直到重新索引。
 * @author AsterWinston
 */
@Slf4j
@Component
public class RocketMqProducer {

    private final RocketMQTemplate rocketMqTemplate;
    private final MessageTxService messageTxService;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final String TOPIC = "message-topic";

    public RocketMqProducer(RocketMQTemplate rocketMqTemplate, MessageTxService messageTxService) {
        this.rocketMqTemplate = rocketMqTemplate;
        this.messageTxService = messageTxService;
    }

    /**
     * 将消息持久化到MySQL（通过{@link MessageTxService}），然后发送
     * 最大努力投递的RocketMQ事件用于Elasticsearch索引。持久化先行，
     * 确保即使消息队列宕机消息也不会丢失。
     *
     * @param ctx 要持久化和索引的待处理消息上下文
     */
    public void publish(PendingMessageContext ctx) {
        Long msgId = ctx.getMessage().getMsgId();
        messageTxService.persist(ctx);
        try {
            String json = OBJECT_MAPPER.writeValueAsString(ctx.getMessage());
            rocketMqTemplate.syncSend(TOPIC, json, 3000);
            log.debug("Index event sent: msgId={}", msgId);
        } catch (Exception e) {
            log.warn("Index event failed for msgId={}, will rely on direct ES indexing: {}",
                    msgId, e.getMessage());
        }
    }
}