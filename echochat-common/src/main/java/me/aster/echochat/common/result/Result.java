package me.aster.echochat.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一API响应包装类，包含状态码、消息、可选数据和时间戳。
 *
 * @param <T> 响应负载的类型
 * @author AsterWinston
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

    /** 状态码 */
    private int code;
    /** 人类可读的消息 */
    private String message;
    /** 响应负载，为null时在JSON中省略 */
    private T data;
    /** 响应时间戳（毫秒） */
    private long timestamp;

    /**
     * 构建带数据的成功结果。
     *
     * @param data 响应负载
     * @param <T>  负载类型
     * @return 成功的 {@link Result}
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), "success", data, System.currentTimeMillis());
    }

    /**
     * 构建无数据的成功结果。
     *
     * @param <T> 负载类型
     * @return 成功的 {@link Result}
     */
    public static <T> Result<T> ok() {
        return ok(null);
    }

    /**
     * 构建带自定义错误码和消息的失败结果。
     *
     * @param code    错误码
     * @param message 错误描述
     * @param <T>     负载类型
     * @return 失败的 {@link Result}
     */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null, System.currentTimeMillis());
    }

    /**
     * 根据 {@link ResultCode} 枚举构建失败结果。
     *
     * @param code 结果码
     * @param <T>  负载类型
     * @return 失败的 {@link Result}
     */
    public static <T> Result<T> fail(ResultCode code) {
        return new Result<>(code.getCode(), code.getMessage(), null, System.currentTimeMillis());
    }
}