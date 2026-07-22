package me.aster.echochat.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 表示当前用户设备信息的 DTO。
 * @author AsterWinston
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceInfo {

    /** 唯一设备标识符。 */
    private String deviceId;

    /** 设备平台（例如 web, android, ios）。 */
    private String platform;

    /** 最近已知的 IP 地址（当前未实现，预留字段）。 */
    private String ip;

    /** 最近一次登录的时间戳。 */
    private LocalDateTime loginAt;

    /** 设备当前是否在线。 */
    private Boolean online;

    /** 该设备是否为发起当前请求的设备。 */
    private Boolean current;
}
