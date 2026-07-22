package me.aster.echochat.message.websocket;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.websocketx.*;
import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.message.service.impl.MessageReadService;
import me.aster.echochat.message.service.ConversationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Netty通道入站处理器，拦截初始HTTP升级请求，从URI中提取并验证JWT令牌，
 * 执行WebSocket握手，并安装{@link WebSocketFrameHandler}用于后续帧处理。
 * @author AsterWinston
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class WebSocketAuthHandler extends ChannelInboundHandlerAdapter {

    private final WebSocketChannelManager channelManager;
    private final WebSocketPushService pushService;
    private final MessageReadService messageReadService;
    private final ConversationService conversationService;
    private final String jwtSecret;
    private final String wsUrl;

    private static final String AMPERSAND = "&";
    private static final String EQUALS_SIGN = "=";
    private static final String TOKEN_PARAM = "token";

    /**
     * 使用所需依赖和WebSocket URL配置构造认证处理器。
     *
     * @param channelManager      WebSocket通道注册表
     * @param pushService         用于连接生命周期事件的推送服务
     * @param messageReadService  消息已读跟踪服务
     * @param conversationService 会话服务
     * @param jwtSecret           Base64编码的JWT签名密钥
     * @param wsHost              WebSocket服务器主机
     * @param wsPort              WebSocket服务器端口
     */
    public WebSocketAuthHandler(WebSocketChannelManager channelManager,
                                WebSocketPushService pushService,
                                MessageReadService messageReadService,
                                ConversationService conversationService,
                                @Value("${jwt.secret}") String jwtSecret,
                                @Value("${websocket.host:localhost}") String wsHost,
                                @Value("${websocket.port:9000}") int wsPort) {
        this.channelManager = channelManager;
        this.pushService = pushService;
        this.messageReadService = messageReadService;
        this.conversationService = conversationService;
        this.jwtSecret = jwtSecret;
        this.wsUrl = "ws://" + wsHost + ":" + wsPort + "/ws";
    }

    /**
     * @param ctx 通道处理器上下文
     * @param msg 传入的消息（预期为{@link FullHttpRequest}）
     * @throws Exception 如果握手过程中发生错误
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            FullHttpRequest request = (FullHttpRequest) msg;
            String uri = request.uri();
            String token = extractToken(uri);

            if (token == null) {
                sendHttpResponse(ctx, request, HttpResponseStatus.UNAUTHORIZED);
                return;
            }

            WebSocketSession session = WebSocketSession.authenticate(token, jwtSecret);
            if (session == null) {
                sendHttpResponse(ctx, request, HttpResponseStatus.UNAUTHORIZED);
                return;
            }

            ctx.channel().attr(ChannelAttributes.SESSION).set(session);

            WebSocketServerHandshakerFactory factory =
                    new WebSocketServerHandshakerFactory(wsUrl, null, false);
            WebSocketServerHandshaker handshaker = factory.newHandshaker(request);
            if (handshaker == null) {
                WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
                return;
            }
            handshaker.handshake(ctx.channel(), request);

            channelManager.register(session.getUid(), session.getDeviceId(), ctx.channel());
            pushService.handleConnect(session.getUid(), session.getDeviceId());
            log.info("WebSocket connected: uid={}, deviceId={}", session.getUid(), session.getDeviceId());

            ctx.pipeline().remove(this);
            ctx.pipeline().addLast(new WebSocketFrameHandler(channelManager, session, pushService, messageReadService, conversationService));
            return;
        }
        super.channelRead(ctx, msg);
    }

    /**
     * 从WebSocket URI查询参数{@code token}中提取JWT令牌。
     *
     * @param uri 完整的请求URI
     * @return 令牌字符串，如果不存在则返回null
     */
    private String extractToken(String uri) {
        int queryIdx = uri.indexOf('?');
        if (queryIdx < 0 || queryIdx == uri.length() - 1) {
            return null;
        }
        String query = uri.substring(queryIdx + 1);
        for (String pair : query.split(AMPERSAND)) {
            int eq = pair.indexOf(EQUALS_SIGN);
            if (eq > 0 && TOKEN_PARAM.equals(pair.substring(0, eq))) {
                String value = pair.substring(eq + 1);
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    /**
     * 写入一个简单的HTTP响应（如401未授权）。注意：当前未关闭通道。
     *
     * @param ctx     通道处理器上下文
     * @param request 原始的HTTP升级请求
     * @param status  要返回的HTTP状态码
     */
    private void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest request, HttpResponseStatus status) {
        io.netty.handler.codec.http.FullHttpResponse response =
                new io.netty.handler.codec.http.DefaultFullHttpResponse(request.protocolVersion(), status);
        ctx.writeAndFlush(response);
    }
}