package me.aster.echochat.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.common.result.Result;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;

/**
 * 全局异常处理器，将未处理的错误转换为JSON响应。
 * @author AsterWinston
 */
@Slf4j
@Configuration
@Order(-2)
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    /** 用于错误响应体的JSON序列化器。 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 处理异常并写入标准的 {@link Result} JSON响应。
     *
     * @param exchange 当前服务器交换对象
     * @param ex       抛出的异常
     * @return 写入错误响应后完成的Mono
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, @NonNull Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        HttpStatus httpStatus;
        int resultCode;
        String message;

        if (ex instanceof ResponseStatusException rse) {
            httpStatus = HttpStatus.valueOf(rse.getStatusCode().value());
            resultCode = httpStatus.value();
            message = rse.getReason() != null ? rse.getReason() : httpStatus.getReasonPhrase();
        } else if (isConnectException(ex)) {
            httpStatus = HttpStatus.SERVICE_UNAVAILABLE;
            resultCode = 503;
            message = "Service temporarily unavailable";
            log.warn("Gateway backend connection failed: path={}", exchange.getRequest().getPath());
        } else {
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
            resultCode = 500;
            message = "Internal server error";
            log.error("Gateway system exception: path={}", exchange.getRequest().getPath(), ex);
        }

        response.setStatusCode(httpStatus);

        Result<Void> result = Result.fail(resultCode, message);
        try {
            byte[] bytes = OBJECT_MAPPER.writeValueAsBytes(result);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    /**
     * 遍历给定异常对象的因果链，判断其中是否存在 {@link java.net.ConnectException}。
     * 用于检测后端连接失败并返回503响应。
     *
     * @param ex 要检查的顶层异常
     * @return 如果在因果链中发现ConnectException则返回 {@code true}
     */
    private boolean isConnectException(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof ConnectException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}