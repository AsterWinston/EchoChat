package me.aster.echochat.message.websocket;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import me.aster.echochat.common.constant.BusinessConstants;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * 基于Netty的WebSocket服务器，在配置的端口上启动，设置HTTP编解码器、
 * 认证处理器和空闲状态处理器的管道。
 * @author AsterWinston
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NettyWebSocketServer {

    private final WebSocketAuthHandler authHandler;

    /** WebSocket服务器端口，默认9000 */
    @Value("${websocket.port:9000}")
    private int port;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    /**
     * 启动Netty服务器并绑定到配置的端口。
     *
     * @throws InterruptedException 如果绑定被中断
     */
    @PostConstruct
    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline()
                                .addLast(new HttpServerCodec())
                                .addLast(new HttpObjectAggregator(BusinessConstants.MAX_HTTP_CONTENT_LENGTH))
                                .addLast(authHandler)
                                .addLast(new IdleStateHandler(90, 0, 0));
                    }
                })
                .option(ChannelOption.SO_BACKLOG, BusinessConstants.SO_BACKLOG)
                .childOption(ChannelOption.SO_KEEPALIVE, true);

        serverChannel = bootstrap.bind(port).sync().channel();
        log.info("Netty WebSocket Server started on port {}", port);
    }

    /** 优雅地关闭服务器通道和事件循环组。 */
    @PreDestroy
    public void stop() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }
}