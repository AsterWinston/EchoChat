package me.aster.echochat.message.mq;

import lombok.Builder;
import lombok.Data;
import me.aster.echochat.message.entity.Message;
import java.util.List;

/**
 * 在RocketMQ事务发送过程中携带聊天消息及其会话路由上下文：
 * 本地事务监听器从此上下文持久化消息和会话行，
 * 调用方仅在本地事务提交后执行Redis/WebSocket副作用。
 * @author AsterWinston
 */
@Data
@Builder
public class PendingMessageContext {

    /** 要持久化和索引的聊天消息。 */
    private Message message;

    /** 单聊：接收者UID（群消息时为null）。 */
    private Long singleToUid;

    /** 群聊：排除发送者的成员UID（单聊时为空列表）。 */
    private List<Long> groupReceiverUids;

    /** 群聊：当写扩散（<= 500成员）应更新每个成员的会话时为true。 */
    private boolean groupFanOut;
}