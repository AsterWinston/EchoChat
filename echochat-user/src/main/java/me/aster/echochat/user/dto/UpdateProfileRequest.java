package me.aster.echochat.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 部分个人资料更新的 DTO。仅非空字段会被应用。
 * @author AsterWinston
 */
@Data
public class UpdateProfileRequest {

    /** 显示昵称。 */
    @Size(min = 1, max = 32, message = "must be 1-32 characters")
    private String nickname;

    /** 头像 URL。 */
    @Size(max = 512, message = "must not exceed 512 characters")
    private String avatar;

    /** 个性签名。 */
    @Size(max = 200, message = "must not exceed 200 characters")
    private String signature;

    /** 性别：0 未知，1 男性，2 女性。 */
    @Min(value = 0, message = "must be 0, 1 or 2")
    @Max(value = 2, message = "must be 0, 1 or 2")
    private Integer gender;

    /** 年龄（年份）。 */
    @Min(value = 0, message = "must be non-negative")
    @Max(value = 150, message = "must not exceed 150")
    private Integer age;

    /** 邮箱地址。 */
    @Email(message = "must be a valid email")
    @Size(max = 128, message = "must not exceed 128 characters")
    private String email;

    /**
     * 将请求中的非空字段转换为字段名到值的映射。
     *
     * @return 仅包含请求中存在的字段的映射
     */
    public Map<String, Object> toUpdatesMap() {
        Map<String, Object> updates = new LinkedHashMap<>(16);
        if (nickname != null) {
            updates.put("nickname", nickname);
        }
        if (avatar != null) {
            updates.put("avatar", avatar);
        }
        if (signature != null) {
            updates.put("signature", signature);
        }
        if (gender != null) {
            updates.put("gender", gender);
        }
        if (age != null) {
            updates.put("age", age);
        }
        if (email != null) {
            updates.put("email", email);
        }
        return updates;
    }
}
