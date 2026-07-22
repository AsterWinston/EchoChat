package me.aster.echochat.moment.service;

import me.aster.echochat.moment.entity.Moment;
import me.aster.echochat.moment.entity.MomentComment;
import me.aster.echochat.moment.entity.MomentLike;

import java.util.List;
import java.util.Map;

/**
 * Moment（动态）操作的服务接口：发布、删除、点赞、评论、时间线获取以及用户动态浏览。
 * @author AsterWinston
 */
public interface MomentService {

    /**
     * 发布一条新动态。对于受限可见性，将屏蔽的UID写入moment_privacy表。
     * 将动态分发到所有好友的feed_timeline（写扩散模式）。
     *
     * @param uid        作者用户ID
     * @param content    文本内容（有媒体时可选）
     * @param media      媒体URL（有内容时可选）
     * @param visibility "public"或"restricted"；默认为"public"
     * @param blockUids  可见性为restricted时需要屏蔽的用户ID列表
     * @param showRange  时间范围过滤标签（如"3d"、"30d"、"180d"）
     * @return 创建的动态
     */
    Moment publish(Long uid, String content, String media, String visibility,
                   List<Long> blockUids, String showRange);

    /**
     * 软删除一条动态，并级联删除关联的点赞、评论、时间线条目和隐私记录。
     *
     * @param uid      请求用户ID（必须是动态作者）
     * @param momentId 要删除的动态ID
     */
    void deleteMoment(Long uid, Long momentId);

    /**
     * 获取用户的时间线动态，含隐私过滤。
     * 排除已删除、受限（用户被屏蔽时）以及超出展示范围的动态。
     *
     * @param uid      查看者用户ID
     * @param beforeId 分页游标动态ID（首页时传null）
     * @param limit    最多返回的动态数
     * @return 时间线条目Map列表
     */
    List<Map<String, Object>> getFeed(Long uid, Long beforeId, int limit);

    /**
     * 获取某个用户的分页动态列表。受限动态对被屏蔽的查看者隐藏，但作者自身始终可见。
     *
     * @param viewerUid 查看者用户ID
     * @param targetUid 目标用户ID（要获取其动态的用户）
     * @param beforeId  分页游标动态ID（首页时传null）
     * @param limit     最多返回的动态数
     * @return 过滤后的可见动态列表
     */
    List<Moment> getUserMoments(Long viewerUid, Long targetUid, Long beforeId, int limit);

    /**
     * 获取单条动态详情；被屏蔽的用户查看受限动态时返回404。
     *
     * @param uid      查看者用户ID
     * @param momentId 要获取的动态ID
     * @return 授权访问的动态
     */
    Moment getMomentDetail(Long uid, Long momentId);

    /**
     * 点赞一条动态；若已点赞或无访问权限则拒绝。
     *
     * @param uid      执行点赞的用户ID
     * @param momentId 要点赞的动态ID
     */
    void likeMoment(Long uid, Long momentId);

    /**
     * 取消点赞；若未点赞或无访问权限则拒绝。
     *
     * @param uid      取消点赞的用户ID
     * @param momentId 要取消点赞的动态ID
     */
    void unlikeMoment(Long uid, Long momentId);

    /**
     * 获取某条动态的点赞列表；被屏蔽的用户返回404。
     *
     * @param uid      查看者用户ID
     * @param momentId 要获取点赞的动态ID
     * @return 该动态的点赞列表
     */
    List<MomentLike> getLikes(Long uid, Long momentId);

    /**
     * 添加评论，可指定回复某个评论者。
     *
     * @param uid        评论者用户ID
     * @param momentId   要评论的动态ID
     * @param replyToUid 被回复的用户ID（顶级评论时传null）
     * @param content    评论文本内容
     * @return 创建的评论
     */
    MomentComment comment(Long uid, Long momentId, Long replyToUid, String content);

    /**
     * 删除评论；仅评论作者或动态作者可以删除。
     *
     * @param uid       请求用户ID
     * @param commentId 要删除的评论ID
     */
    void deleteComment(Long uid, Long commentId);

    /**
     * 获取某条动态的评论列表；被屏蔽的用户返回404。
     *
     * @param uid      查看者用户ID
     * @param momentId 要获取评论的动态ID
     * @return 该动态的评论列表
     */
    List<MomentComment> getComments(Long uid, Long momentId);
}
