package me.aster.echochat.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.common.exception.BusinessException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.io.InputStream;
import java.util.Map;

/**
 * Feign错误解码器配置，从远程服务响应中提取错误码和错误信息，并将其转换为 {@link BusinessException}。
 * @author AsterWinston
 */
@Slf4j
@Configuration
public class FeignErrorConfig {

    private static final String ERROR_CODE_KEY = "code";
    private static final String ERROR_MESSAGE_KEY = "message";

    /**
     * @return 一个 {@link ErrorDecoder}，解析响应体中的结构化错误数据
     */
    @Bean
    public ErrorDecoder errorDecoder() {
        return (methodKey, response) -> {
            try {
                Response.Body body = response.body();
                if (body != null) {
                    byte[] bytes;
                    try (InputStream is = body.asInputStream()) {
                        bytes = is.readAllBytes();
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> result = new ObjectMapper().readValue(bytes, Map.class);
                    if (result.containsKey(ERROR_CODE_KEY) && result.containsKey(ERROR_MESSAGE_KEY)) {
                        int code = ((Number) result.get(ERROR_CODE_KEY)).intValue();
                        String message = (String) result.get(ERROR_MESSAGE_KEY);
                        return new BusinessException(code, message);
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to parse Feign error response body", e);
            }
            Exception defaultEx = new ErrorDecoder.Default().decode(methodKey, response);
            if (defaultEx instanceof FeignException fe) {
                return new BusinessException(fe.status(),
                        fe.getMessage() != null ? fe.getMessage() : "Remote service call failed");
            }
            return defaultEx;
        };
    }
}