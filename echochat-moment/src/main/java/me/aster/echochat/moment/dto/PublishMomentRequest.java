package me.aster.echochat.moment.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 发布动态的DTO。
 * @author AsterWinston
 */
@Data
public class PublishMomentRequest {

    /** 动态的文本内容。 */
    @Size(max = 2000, message = "must not exceed 2000 characters")
    private String content;

    /** JSON数组形式的媒体描述符字符串。 */
    @Size(max = 10000, message = "must not exceed 10000 characters")
    private String media;

    /** 可见性策略。 */
    @Pattern(regexp = "public|restricted", message = "must be public or restricted")
    private String visibility = "public";

    /** 可见性为restricted时被排除查看的UID列表。 */
    @Size(max = 500, message = "cannot block more than 500 users")
    private List<Long> blockUids;

    /** 动态展示时间范围偏好（3d / 30d / 180d / all）。 */
    @Pattern(regexp = "3d|30d|180d|all", message = "must be one of 3d/30d/180d/all")
    private String showRange;
}
