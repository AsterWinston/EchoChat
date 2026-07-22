package me.aster.echochat.message.websocket;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.common.constant.BusinessConstants;
import me.aster.echochat.message.service.ConversationService;
import me.aster.echochat.message.service.impl.MessageReadService;

/**
 * Netty处理器，用于处理入站WebSocket帧。
 * 处理文本消息（ping、已读回执、正在输入指示器）、关闭帧、ping/pong
 * 以及连接生命周期事件（空闲和断连）。
 * @author AsterWinston
 */
@Slf4j
public class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final String TYPE_PING = "ping";
    private static final String TYPE_READ = "read";
    private static final String TYPE_GROUP_READ = "group_read";
    private static final String TYPE_TYPING = "typing";
    private static final String TYPE_SYNC = "sync";
    private static final String TYPE_ACK = "ack";
    private static final String FIELD_SESSIONS = "sessions";

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final WebSocketChannelManager channelManager;
    private final WebSocketSession session;
    private final WebSocketPushService pushService;
    private final MessageReadService messageReadService;
    private final ConversationService conversationService;

    /**
     * @param channelManager       通道注册表
     * @param session              此连接的已认证会话
     * @param pushService          推送服务
     * @param messageReadService   已读回执服务
     * @param conversationService  会话服务
     */
    public WebSocketFrameHandler(WebSocketChannelManager channelManager, WebSocketSession session,
                                  WebSocketPushService pushService, MessageReadService messageReadService,
                                  ConversationService conversationService) {
        this.channelManager = channelManager;
        this.session = session;
        this.pushService = pushService;
        this.messageReadService = messageReadService;
        this.conversationService = conversationService;
    }

    /**
     * @param ctx   通道处理器上下文
     * @param frame 入站WebSocket帧
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof CloseWebSocketFrame) {
            ctx.close();
        } else if (frame instanceof PingWebSocketFrame) {
            ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
        } else if (frame instanceof TextWebSocketFrame) {
            String text = ((TextWebSocketFrame) frame).text();
            handleTextMessage(ctx, text);
        }
    }

    /**
     * 处理客户端发送的ping（回复pong）、已读回执、正在输入指示器、
     * 离线同步和ACK确认消息。
     *
     * @param ctx  通道上下文
     * @param text JSON文本载荷
     */
    private void handleTextMessage(ChannelHandlerContext ctx, String text) {
        try {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> msg = MAPPER.readValue(text, java.util.Map.class);
            String type = (String) msg.get("type");
            if (TYPE_PING.equals(type)) {
                pushService.handleHeartbeat(session.getUid(), session.getDeviceId());
                ctx.writeAndFlush(new TextWebSocketFrame("{\"type\":\"pong\"}"));
            } else if (TYPE_READ.equals(type)) {
                handleRead(msg);
            } else if (TYPE_GROUP_READ.equals(type)) {
                handleGroupRead(msg);
            } else if (TYPE_TYPING.equals(type)) {
                handleTyping(msg);
            } else if (TYPE_SYNC.equals(type)) {
                handleSync(msg, ctx);
            } else if (TYPE_ACK.equals(type)) {
                handleAck(msg);
            }
        } catch (Exception e) {
            log.warn("Failed to handle WebSocket text message: uid={}", session.getUid(), e);
            try {
                ctx.writeAndFlush(new TextWebSocketFrame(
                        "{\"type\":\"error\",\"reason\":\"invalid_message_format\"}"));
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 处理来自客户端的ACK消息：从待确认ACK跟踪集合中移除已确认的消息ID。
     */
    @SuppressWarnings("unchecked")
    private void handleAck(java.util.Map<String, Object> msg) {
        java.util.Map<String, Object> data = (java.util.Map<String, Object>) msg.get("data");
        if (data == null) {
            return;
        }
        java.util.List<Number> msgIdList = (java.util.List<Number>) data.get("msgIds");
        if (msgIdList == null || msgIdList.isEmpty()) {
            return;
        }
        java.util.List<Long> msgIds = msgIdList.stream().map(Number::longValue).collect(java.util.stream.Collectors.toList());
        pushService.handleAck(session.getUid(), msgIds);
    }

    @SuppressWarnings("unchecked")
    private void handleSync(java.util.Map<String, Object> msg, ChannelHandlerContext ctx) {
        java.util.Map<String, Object> data = (java.util.Map<String, Object>) msg.get("data");
        if (data != null && data.get(FIELD_SESSIONS) instanceof java.util.List) {
            java.util.List<java.util.Map<String, Object>> sessions =
                    (java.util.List<java.util.Map<String, Object>>) data.get("sessions");
            pushService.syncIncrementalMessages(session.getUid(), sessions, ctx.channel());
        } else {
            pushService.syncOfflineMessages(session.getUid(), ctx.channel());
        }
    }

    /**
     * 处理来自客户端的已读回执：将通过参数传递的发送者标识和已读序号上限
     * 传递给标记已读逻辑，间接确定会话类型和需要标记的消息范围。
     */
    @SuppressWarnings("unchecked")
    private void handleRead(java.util.Map<String, Object> msg) {
        java.util.Map<String, Object> data = (java.util.Map<String, Object>) msg.get("data");
        if (data == null) {
            return;
        }
        Number fromUidNum = (Number) data.get("fromUid");
        Number toSeqNum = (Number) data.get("toSeq");
        if (fromUidNum == null || toSeqNum == null) {
            return;
        }
        messageReadService.markMessagesAsRead(session.getUid(), fromUidNum.longValue(), toSeqNum.longValue());
    }

    /**
     * 处理客户端发出的群聊已读标记：前端浏览群消息窗口时主动发送，
     * 由服务端触发群内所有未读消息的已读回执推送。
     */
    @SuppressWarnings("unchecked")
    private void handleGroupRead(java.util.Map<String, Object> msg) {
        java.util.Map<String, Object> data = (java.util.Map<String, Object>) msg.get("data");
        if (data == null) {
            return;
        }
        Object gidObj = data.get("gid");
        if (gidObj == null) {
            return;
        }
        long gid;
        if (gidObj instanceof Number) {
            gid = ((Number) gidObj).longValue();
        } else {
            try {
                gid = Long.parseLong(gidObj.toString());
            } catch (NumberFormatException e) {
                return;
            }
        }
        conversationService.markAsRead(session.getUid(), BusinessConstants.SESSION_TYPE_GROUP, String.valueOf(gid));
    }

    /**
     * 处理来自客户端的正在输入指示器：将正在输入事件转发给对话伙伴。
     */
    @SuppressWarnings("unchecked")
    private void handleTyping(java.util.Map<String, Object> msg) {
        java.util.Map<String, Object> data = (java.util.Map<String, Object>) msg.get("data");
        if (data == null) {
            return;
        }
        Object toUidObj = data.get("toUid");
        Long toUidLong = null;
        if (toUidObj instanceof Number) {
            toUidLong = ((Number) toUidObj).longValue();
        } else if (toUidObj instanceof String) {
            try {
                toUidLong = Long.parseLong((String) toUidObj);
            } catch (NumberFormatException ignored) {
            }
        }
        if (toUidLong == null) {
            return;
        }
        pushService.pushTypingIndicator(toUidLong, session.getUid());
    }

    /**
     * 当通道变为非活跃状态时调用（客户端断连或超时）。
     * 清理通道注册。
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        cleanup(ctx);
    }

    /**
     * 当通道上发生异常时调用。
     * 记录错误并清理连接。
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("WebSocket error: uid={}", session.getUid(), cause);
        cleanup(ctx);
    }

    /**
     * 在用户触发的事件上调用，具体而言是检测读空闲超时的IdleStateEvent。
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                log.info("WebSocket idle timeout: uid={}", session.getUid());
                ctx.close();
            }
        }
    }

    /**
     * 注销通道并记录断连。先从本地注册表中移除通道，
     * 这样断连处理器在决定是否删除路由条目时能看到正确的剩余连接状态。
     */
    private void cleanup(ChannelHandlerContext ctx) {
        channelManager.unregister(session.getUid(), session.getDeviceId(), ctx.channel());
        pushService.handleDisconnect(session.getUid(), session.getDeviceId());
        log.info("WebSocket disconnected: uid={}, deviceId={}", session.getUid(), session.getDeviceId());
    }
}