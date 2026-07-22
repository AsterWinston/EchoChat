package me.aster.echochat.common.exception;

import me.aster.echochat.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务逻辑异常，携带错误码，用于生成结构化的API响应。
 * @author AsterWinston
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 数值型错误码 */
    private final int code;

    /**
     * @param code    错误码
     * @param message 错误描述
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * @param resultCode 预定义的 {@link ResultCode}
     */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    /**
     * @param resultCode 预定义的 {@link ResultCode}
     * @param detail     可选的覆盖消息；如果为null则使用resultCode的消息
     */
    public BusinessException(ResultCode resultCode, String detail) {
        super(detail != null ? detail : resultCode.getMessage());
        this.code = resultCode.getCode();
    }
}