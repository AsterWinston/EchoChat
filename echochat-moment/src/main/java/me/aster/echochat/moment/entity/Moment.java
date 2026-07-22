package me.aster.echochat.moment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * moment表的实体映射，代表一条动态/时间线帖子。
 * @author AsterWinston
 */
@Data
@TableName("moment")
public class Moment {

    /** 雪花算法生成的动态ID */
    @TableId(type = IdType.INPUT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long momentId;

    /** 发布者UID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long uid;

    /** 文本内容 */
    private String content;

    /** JSON数组形式的媒体对象：[{url, thumb_url, type, width, height}]，最多9个 */
    private String media;

    /** 可见性：public（公开）或restricted（受限，应用屏蔽列表） */
    private String visibility;

    /** 时间范围过滤：3d、30d、180d、all，或null表示不限 */
    private String showRange;

    /** 发布时间戳 */
    private LocalDateTime createdAt;

    /** 最后更新时间戳 */
    private LocalDateTime updatedAt;

    /** 软删除标记：0=活跃，1=已删除 */
    private Integer isDeleted;

    // --- 供getUserMoments API使用的临时富化字段（不持久化） ---

    /** 当前查看者是否已点赞该动态。 */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Boolean isLiked;

    /** 该动态的总点赞数（从moment_like表统计得出）。 */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Integer likeCount;

    /** 该动态的总评论数（从moment_comment表统计得出）。 */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Integer commentCount;
}
