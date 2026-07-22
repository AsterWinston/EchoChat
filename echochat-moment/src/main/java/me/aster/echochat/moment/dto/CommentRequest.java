package me.aster.echochat.moment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 评论动态的DTO。
 * @author AsterWinston
 */
@Data
public class CommentRequest {

    /** 被回复评论者的UID（可选）。 */
    private Long replyToUid;

    /** 评论文本。 */
    @NotBlank(message = "content is required")
    @Size(max = 500, message = "must not exceed 500 characters")
    private String content;
}
