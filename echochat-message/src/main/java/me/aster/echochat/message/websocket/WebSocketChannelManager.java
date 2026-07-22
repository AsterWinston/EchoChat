package me.aster.echochat.message.websocket;

import io.netty.channel.Channel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理以用户ID和设备ID为键的WebSocket通道注册，
 * 支持每个用户多设备，并提供在线状态查询。
 * @author AsterWinston
 */
@Component
public class WebSocketChannelManager {

    /** 按用户ID分组，再按设备ID分组的通道 */
    private final Map<Long, Map<String, Set<Channel>>> userChannels = new ConcurrentHashMap<>();

    /**
     * @param uid      用户ID
     * @param deviceId 设备标识符
     * @param channel  要注册的Netty通道
     */
    public void register(Long uid, String deviceId, Channel channel) {
        userChannels.computeIfAbsent(uid, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(deviceId, k -> ConcurrentHashMap.newKeySet())
                .add(channel);
    }

    /**
     * 注销并关闭Netty通道。
     *
     * @param uid      用户ID
     * @param deviceId 设备标识符
     * @param channel  要注销并关闭的Netty通道
     */
    public void unregister(Long uid, String deviceId, Channel channel) {
        channel.close();

        Map<String, Set<Channel>> deviceMap = userChannels.get(uid);
        if (deviceMap != null) {
            Set<Channel> deviceChannels = deviceMap.get(deviceId);
            if (deviceChannels != null) {
                deviceChannels.remove(channel);
                if (deviceChannels.isEmpty()) {
                    deviceMap.remove(deviceId);
                }
            }
            if (deviceMap.isEmpty()) {
                userChannels.remove(uid);
            }
        }
    }

    /**
     * @param uid 用户ID
     * @return 该用户所有设备上的所有活跃通道
     */
    public Collection<Channel> getUserChannels(Long uid) {
        Map<String, Set<Channel>> deviceMap = userChannels.get(uid);
        if (deviceMap == null || deviceMap.isEmpty()) {
            return List.of();
        }
        List<Channel> result = new ArrayList<>();
        for (Set<Channel> deviceChannels : deviceMap.values()) {
            result.addAll(deviceChannels);
        }
        return result;
    }

    /**
     * @param uid 用户ID
     * @return 如果用户至少有一个活跃通道则返回true
     */
    public boolean isOnline(Long uid) {
        Map<String, Set<Channel>> deviceMap = userChannels.get(uid);
        return deviceMap != null && !deviceMap.isEmpty();
    }
}