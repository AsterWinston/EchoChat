package me.aster.echochat.common.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.common.result.Result;
import me.aster.echochat.common.result.ResultCode;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.stream.Collectors;

/**
 * 全局 {@code @RestControllerAdvice}，将异常转换为统一的 {@link Result} 响应。
 * @author AsterWinston
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final int HTTP_STATUS_MIN = 100;
    private static final int HTTP_STATUS_MAX = 600;
    private static final String BROKEN_PIPE_MSG = "Broken pipe";
    private static final String ABORT_MSG = "abort";

    /**
     * 处理 {@link BusinessException}，返回其错误码和消息。
     * HTTP状态码根据异常的错误码推导（如 400、403、404）。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        HttpStatus status = resolveHttpStatus(e.getCode());
        if (status.is5xxServerError()) {
            log.error("Business exception: code={}, message={}", e.getCode(), e.getMessage());
        } else {
            log.warn("Business exception: code={}, message={}", e.getCode(), e.getMessage());
        }
        return ResponseEntity.status(status).body(Result.fail(e.getCode(), e.getMessage()));
    }

    /** 处理{@link IllegalArgumentException}（涵盖输入验证、格式转换等非法参数场景）。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Parameter exception: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.fail(ResultCode.BAD_REQUEST.getCode(), e.getMessage()));
    }

    /** 处理缺少必需请求参数的错误。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.fail(ResultCode.BAD_REQUEST.getCode(), "Missing required parameter: " + e.getParameterName()));
    }

    /**
     * 处理 {@code @Valid} 请求体和表单绑定上的Bean Validation失败，
     * 包括 {@code MethodArgumentNotValidException}（{@link BindException} 的子类）。
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(ResultCode.BAD_REQUEST.getCode(), message.isEmpty() ? "Validation failed" : message));
    }

    /** 处理 {@code @Validated} 方法参数（service层、非controller bean）上的约束违反。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("Constraint violation: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(ResultCode.BAD_REQUEST.getCode(), message.isEmpty() ? "Validation failed" : message));
    }

    /** 处理controller中 {@code @RequestParam}/{@code @PathVariable} 约束的验证失败（Spring 6.1+）。 */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Result<Void>> handleHandlerMethodValidation(HandlerMethodValidationException e) {
        String message = e.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Handler method validation failed: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(ResultCode.BAD_REQUEST.getCode(), message.isEmpty() ? "Validation failed" : message));
    }

    /** 处理不支持的HTTP方法错误（如对POST端点发送GET请求）。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("Http method not supported: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(Result.fail(ResultCode.METHOD_NOT_ALLOWED));
    }

    /** 处理格式错误的JSON请求体错误。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("Request body parse failed: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.fail(ResultCode.BAD_REQUEST.getCode(), "Invalid request body format, check JSON"));
    }

    /** 处理路径变量或请求参数的类型不匹配错误（如Long类型位置传入"abc"）。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("Parameter type conversion failed: name={}, value={}", e.getName(), e.getValue());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(ResultCode.BAD_REQUEST.getCode(), "Parameter type error: " + e.getName()));
    }

    /** 处理I/O异常：客户端主动断开连接（Broken pipe / abort）时忽略（debug级别）；其他I/O异常则记录错误并返回500。 */
    @ExceptionHandler(java.io.IOException.class)
    public ResponseEntity<Result<Void>> handleIoException(java.io.IOException e) {
        String msg = e.getMessage();
        if (isClientDisconnect(msg)) {
            log.debug("Client disconnected: {}", msg);
            return ResponseEntity.status(HttpStatus.OK).body(Result.ok());
        }
        log.error("IO exception: ", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(ResultCode.INTERNAL_ERROR));
    }

    /** 处理异步请求不可用（流式传输时客户端断开）——debug级别，无害。 */
    @ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestNotUsableException.class)
    public void handleAsyncNotUsable(org.springframework.web.context.request.async.AsyncRequestNotUsableException e) {
        log.debug("Async request not usable: {}", e.getMessage());
    }

    /** 处理异步超时——info级别，无害。 */
    @ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestTimeoutException.class)
    public void handleAsyncTimeout(org.springframework.web.context.request.async.AsyncRequestTimeoutException e) {
        log.info("Async request timeout: {}", e.getMessage());
    }

    /** 兜底异常处理器，处理未预期的异常，返回500 Internal Server Error。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        log.error("System exception: ", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.fail(ResultCode.INTERNAL_ERROR));
    }

    /**
     * 判断消息是否为客户端主动断开连接（包含"abort"或"Broken pipe"）。
     *
     * @param msg 异常消息
     * @return 如果消息包含断开连接标识则返回 {@code true}
     */
    private static boolean isClientDisconnect(String msg) {
        return msg != null && (msg.contains(ABORT_MSG) || msg.contains(BROKEN_PIPE_MSG));
    }

    /**
     * 从业务错误码解析HTTP状态码。
     * 100-599范围内的错误码直接解析；其他错误码默认返回400。
     *
     * @param code 业务错误码
     * @return 对应的HTTP状态码
     */
    private static HttpStatus resolveHttpStatus(int code) {
        if (code >= HTTP_STATUS_MIN && code < HTTP_STATUS_MAX) {
            HttpStatus status = HttpStatus.resolve(code);
            if (status != null) {
                return status;
            }
        }
        return HttpStatus.BAD_REQUEST;
    }
}