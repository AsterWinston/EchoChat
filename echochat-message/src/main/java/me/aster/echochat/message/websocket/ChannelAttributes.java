package me.aster.echochat.message.websocket;

import io.netty.util.AttributeKey;

/**
 * 持有Netty {@link AttributeKey}常量，用于在通道上存储会话数据。
 * @author AsterWinston
 */
public final class ChannelAttributes {

    private ChannelAttributes() {}

    /** 已认证WebSocket会话的属性键 */
    public static final AttributeKey<WebSocketSession> SESSION = AttributeKey.valueOf("session");
}