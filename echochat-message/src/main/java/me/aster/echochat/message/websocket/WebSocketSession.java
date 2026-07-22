package me.aster.echochat.message.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 表示一个已认证的WebSocket会话，封装用户ID和设备ID。
 * 提供JWT认证和消息载荷构建的静态辅助方法。
 * @author AsterWinston
 */
@Getter
@Slf4j
public class WebSocketSession {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Long uid;

    private final String deviceId;

    /**
     * @param uid      已认证的用户ID
     * @param deviceId 设备标识符
     */
    public WebSocketSession(Long uid, String deviceId) {
        this.uid = uid;
        this.deviceId = deviceId;
    }

    /**
     * @param tokenParam JWT令牌字符串
     * @param jwtSecret  Base64编码的签名密钥
     * @return 已认证的会话，如果令牌无效或不是access令牌则返回null
     */
    public static WebSocketSession authenticate(String tokenParam, String jwtSecret) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtSecret));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(tokenParam)
                    .getPayload();

            String type = claims.get("type", String.class);
            if (!"access".equals(type)) {
                return null;
            }

            Long uid = claims.get("uid", Long.class);
            String deviceId = claims.get("device_id", String.class);
            return new WebSocketSession(uid, deviceId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 使用给定的类型和数据构建JSON消息载荷。
     *
     * @param type 消息类型字符串
     * @param data 消息数据（可以是JSON字符串或对象）
     * @return JSON载荷字符串
     */
    public static String buildMessage(String type, Object data) {
        try {
            Map<String, Object> msg = new LinkedHashMap<>(16);
            msg.put("type", type);
            msg.put("data", data);
            msg.put("timestamp", System.currentTimeMillis());
            return OBJECT_MAPPER.writeValueAsString(msg);
        } catch (Exception e) {
            return "{\"type\":\"error\"}";
        }
    }

    /**
     * 构建已读回执JSON载荷，表示用户已读到指定的序号。
     *
     * @param uid 阅读者的用户ID
     * @param seq 已读的最高序号
     * @return JSON已读回执载荷字符串
     */
    public static String buildReadReceipt(Long uid, Long seq) {
        try {
            Map<String, Object> msg = new LinkedHashMap<>(16);
            msg.put("type", "read");
            Map<String, Object> data = new LinkedHashMap<>(16);
            data.put("uid", String.valueOf(uid));
            data.put("seq", seq);
            msg.put("data", data);
            msg.put("timestamp", System.currentTimeMillis());
            return OBJECT_MAPPER.writeValueAsString(msg);
        } catch (Exception e) {
            return "{\"type\":\"error\"}";
        }
    }

    /**
     * 为指定用户构建正在输入指示器JSON载荷。
     *
     * @param uid 正在输入的用户的ID
     * @return JSON正在输入载荷字符串
     */
    public static String buildTyping(Long uid) {
        try {
            Map<String, Object> msg = new LinkedHashMap<>(16);
            msg.put("type", "typing");
            Map<String, Object> data = new LinkedHashMap<>(16);
            data.put("uid", String.valueOf(uid));
            msg.put("data", data);
            msg.put("timestamp", System.currentTimeMillis());
            return OBJECT_MAPPER.writeValueAsString(msg);
        } catch (Exception e) {
            return "{\"type\":\"error\"}";
        }
    }

    /**
     * 构建消息撤回的WebSocket载荷。
     *
     * @param msgId 被撤回的消息ID
     * @return JSON撤回载荷字符串
     */
    public static String buildRecall(Long msgId) {
        try {
            Map<String, Object> msg = new LinkedHashMap<>(16);
            msg.put("type", "recall");
            Map<String, Object> data = new LinkedHashMap<>(16);
            data.put("msgId", String.valueOf(msgId));
            msg.put("data", data);
            msg.put("timestamp", System.currentTimeMillis());
            return OBJECT_MAPPER.writeValueAsString(msg);
        } catch (Exception e) {
            return "{\"type\":\"error\"}";
        }
    }

    /**
     * 构建群聊已读回执JSON载荷，通知消息发送者某条消息已被某用户阅读。
     *
     * @param msgId    被阅读的消息ID
     * @param readerUid 阅读者的用户ID
     * @return JSON已读回执载荷字符串
     */
    public static String buildGroupReadReceipt(Long msgId, Long readerUid) {
        try {
            Map<String, Object> msg = new LinkedHashMap<>(16);
            msg.put("type", "group_read");
            Map<String, Object> data = new LinkedHashMap<>(16);
            data.put("msgId", String.valueOf(msgId));
            data.put("readerUid", String.valueOf(readerUid));
            msg.put("data", data);
            msg.put("timestamp", System.currentTimeMillis());
            return OBJECT_MAPPER.writeValueAsString(msg);
        } catch (Exception e) {
            return "{\"type\":\"error\"}";
        }
    }

    /**
     * 为发送者构建自我回显消息载荷，使用独立的类型
     * 使前端可以将其展示在聊天界面中而不触发通知或会话列表更新。
     *
     * @param json 完整Message实体的JSON序列化
     * @return JSON self_message载荷字符串
     */
    public static String buildSelfMessage(String json) {
        try {
            Map<String, Object> msg = new LinkedHashMap<>(16);
            msg.put("type", "self_message");
            msg.put("data", json);
            msg.put("timestamp", System.currentTimeMillis());
            return OBJECT_MAPPER.writeValueAsString(msg);
        } catch (Exception e) {
            return "{\"type\":\"error\"}";
        }
    }
}