package me.aster.echochat.message.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.common.constant.BusinessConstants;
import me.aster.echochat.common.constant.RedisKeyConstants;
import me.aster.echochat.message.entity.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通过HTTP调用Elasticsearch REST API将聊天消息索引到Elasticsearch
 * 并标记已撤回消息的服务。
 * @author AsterWinston
 */
@Slf4j
@Service
public class ElasticsearchService {

    private final String esUrl;
    private final String esAuthHeader;
    private static final int HTTP_SUCCESS_MIN = 200;
    private static final int HTTP_SUCCESS_MAX = 300;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ElasticsearchService(@Value("${echochat.elasticsearch.uris}") String esUrl,
                                @Value("${echochat.elasticsearch.username:elastic}") String username,
                                @Value("${echochat.elasticsearch.password:}") String password) {
        this.esUrl = esUrl;
        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            this.esAuthHeader = "Basic " + Base64.getEncoder()
                    .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        } else {
            this.esAuthHeader = null;
        }
    }

    @Async
    public void indexMessage(Message message) {
        try {
            Map<String, Object> doc = new LinkedHashMap<>(16);
            doc.put("msgId", message.getMsgId());
            doc.put("sessionType", message.getSessionType());
            doc.put("fromUid", message.getFromUid());
            doc.put("toId", message.getToId());
            doc.put("msgType", message.getMsgType());
            doc.put("content", message.getContent());
            doc.put("sessionId", message.getSessionType() + "_" + message.getToId());
            doc.put("createdAt", message.getCreatedAt() != null ? message.getCreatedAt().toString() : null);
            doc.put("isRecalled", message.getIsRecalled() != null ? message.getIsRecalled() : 0);

            byte[] body = objectMapper.writeValueAsBytes(doc);
            String url = esUrl + "/" + RedisKeyConstants.ES_INDEX_PREFIX + "/_doc/" + message.getMsgId();

            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            try {
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(BusinessConstants.ES_CONNECT_TIMEOUT);
                conn.setReadTimeout(BusinessConstants.ES_READ_TIMEOUT);
                if (esAuthHeader != null) {
                    conn.setRequestProperty("Authorization", esAuthHeader);
                }

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body);
                }

                int code = conn.getResponseCode();

                if (code >= HTTP_SUCCESS_MIN && code < HTTP_SUCCESS_MAX) {
                    log.info("Message indexed to ES: msgId={}", message.getMsgId());
                } else {
                    log.warn("ES index returned status {} for msgId={}", code, message.getMsgId());
                }
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            log.warn("ES index failed for msgId={}: {}", message.getMsgId(), e.getMessage());
        }
    }

    @Async
    public void markRecalled(Long msgId) {
        try {
            String body = "{\"doc\":{\"isRecalled\":1}}";
            String url = esUrl + "/" + RedisKeyConstants.ES_INDEX_PREFIX + "/_update/" + msgId;
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            try {
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(BusinessConstants.ES_CONNECT_TIMEOUT);
                conn.setReadTimeout(BusinessConstants.ES_READ_TIMEOUT);
                if (esAuthHeader != null) {
                    conn.setRequestProperty("Authorization", esAuthHeader);
                }
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
                log.info("ES marked recalled: msgId={}, status={}", msgId, conn.getResponseCode());
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            log.warn("ES mark recalled failed for msgId={}: {}", msgId, e.getMessage());
        }
    }
}