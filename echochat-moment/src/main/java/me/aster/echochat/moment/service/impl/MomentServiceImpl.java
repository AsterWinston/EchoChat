package me.aster.echochat.moment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.common.constant.BusinessConstants;
import me.aster.echochat.common.exception.BusinessException;
import me.aster.echochat.common.result.ResultCode;
import me.aster.echochat.common.util.SnowflakeIdGenerator;
import me.aster.echochat.moment.client.UserFeignClient;
import me.aster.echochat.moment.entity.*;
import me.aster.echochat.moment.mapper.*;
import me.aster.echochat.moment.service.MomentService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * {@link MomentService}的实现，实现了发布/删除、点赞/取消点赞、评论以及基于推模型写扩散和时间线隐私过滤的动态时间线获取。
 * @author AsterWinston
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MomentServiceImpl implements MomentService {

    private static final String VISIBILITY_RESTRICTED = "restricted";

    private final MomentMapper momentMapper;
    private final MomentLikeMapper momentLikeMapper;
    private final MomentCommentMapper momentCommentMapper;
    private final MomentPrivacyMapper momentPrivacyMapper;
    private final FeedTimelineMapper feedTimelineMapper;
    private final UserFeignClient userFeignClient;
    private final SnowflakeIdGenerator idGenerator;

    /**
     * 发布一条新动态。处理受限可见性的隐私限制（记录被屏蔽的用户UID），
     * 然后将动态分发到所有好友的时间线（写扩散模型）。
     *
     * @param uid        作者用户ID
     * @param content    文本内容（有媒体时可选）
     * @param media      媒体URL（有内容时可选）
     * @param visibility "public"或"restricted"；默认为"public"
     * @param blockUids  可见性为restricted时需要屏蔽的用户ID列表
     * @param showRange  时间范围过滤标签（如"3d"、"30d"、"180d"）
     * @return 创建的动态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Moment publish(Long uid, String content, String media, String visibility,
                           List<Long> blockUids, String showRange) {
        boolean hasContent = content != null && !content.isBlank();
        boolean hasMedia = media != null && !media.isBlank();
        if (!hasContent && !hasMedia) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "Content and media cannot both be empty");
        }

        Moment moment = new Moment();
        moment.setMomentId(idGenerator.nextId());
        moment.setUid(uid);
        moment.setContent(content);
        moment.setMedia(media != null && !media.isBlank() ? media : null);
        moment.setVisibility(visibility != null ? visibility : "public");
        moment.setShowRange(showRange);
        moment.setCreatedAt(LocalDateTime.now());
        moment.setUpdatedAt(LocalDateTime.now());
        moment.setIsDeleted(0);
        momentMapper.insert(moment);

        if (VISIBILITY_RESTRICTED.equals(moment.getVisibility()) && blockUids != null && !blockUids.isEmpty()) {
            for (Long blockUid : blockUids) {
                MomentPrivacy privacy = new MomentPrivacy();
                privacy.setMomentId(moment.getMomentId());
                privacy.setBlockUid(blockUid);
                momentPrivacyMapper.insert(privacy);
            }
        }

        List<Long> friendUids = userFeignClient.getFriendUids(uid);
        for (Long friendUid : friendUids) {
            FeedTimeline feed = new FeedTimeline();
            feed.setOwnerUid(friendUid);
            feed.setMomentId(moment.getMomentId());
            feed.setAuthorUid(uid);
            feed.setCreatedAt(moment.getCreatedAt());
            feedTimelineMapper.insert(feed);
        }

        // 也插入到作者自己的时间线中，使其动态出现在自己的时间线里
        FeedTimeline selfFeed = new FeedTimeline();
        selfFeed.setOwnerUid(uid);
        selfFeed.setMomentId(moment.getMomentId());
        selfFeed.setAuthorUid(uid);
        selfFeed.setCreatedAt(moment.getCreatedAt());
        feedTimelineMapper.insert(selfFeed);

        log.info("Moment published: momentId={}, uid={}, friendCount={}", moment.getMomentId(), uid, friendUids.size());
        return moment;
    }

    /**
     * 软删除一条动态，并级联删除所有关联的点赞、评论、时间线条目和隐私记录。
     *
     * @param uid      请求用户ID（必须是动态作者）
     * @param momentId 要删除的动态ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMoment(Long uid, Long momentId) {
        Moment moment = momentMapper.findActiveById(momentId);
        if (moment == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "Moment not found or deleted");
        }
        if (!moment.getUid().equals(uid)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Can only delete own moments");
        }

        moment.setIsDeleted(1);
        moment.setUpdatedAt(LocalDateTime.now());
        momentMapper.updateById(moment);

        momentLikeMapper.deleteByMomentId(momentId);
        momentCommentMapper.deleteByMomentId(momentId);
        feedTimelineMapper.deleteByMomentId(momentId);
        momentPrivacyMapper.deleteByMomentId(momentId);

        log.info("Moment deleted: momentId={}, uid={}", momentId, uid);
    }

    /**
     * 获取用户的时间线动态，含隐私过滤和基于游标的分页。
     * 排除已删除的动态、查看者被屏蔽的受限动态、黑名单用户的动态以及showRange截止时间已过的动态。
     *
     * @param uid      查看者用户ID
     * @param beforeId 游标动态ID（首页时传null）
     * @param limit    最多返回的时间线条目数
     * @return 包含动态数据、点赞数和评论数的时间线条目Map列表
     */
    @Override
    public List<Map<String, Object>> getFeed(Long uid, Long beforeId, int limit) {
        LocalDateTime beforeTime = beforeId != null ? getMomentTime(beforeId) : LocalDateTime.now();
        List<FeedTimeline> feeds = feedTimelineMapper.findByOwner(uid, beforeTime, limit);
        if (feeds.isEmpty()) {
            return List.of();
        }
        List<Long> momentIds = feeds.stream().map(FeedTimeline::getMomentId).toList();
        List<Moment> moments = momentMapper.selectBatchIds(momentIds);
        Map<Long, Moment> momentMap = moments.stream()
                .filter(m -> m.getIsDeleted() == 0)
                .collect(Collectors.toMap(Moment::getMomentId, m -> m));
        List<Map<String, Object>> result = new ArrayList<>();

        Set<Long> likedMomentIds = momentIds.isEmpty()
                ? Set.of()
                : new HashSet<>(momentLikeMapper.findLikedMomentIds(uid, momentIds));

        Set<Long> blockedUids = new HashSet<>(userFeignClient.getBlacklistUids(uid));

        Map<Long, Integer> likeCounts = new HashMap<>(16);
        Map<Long, Integer> commentCounts = new HashMap<>(16);
        Map<Long, Set<Long>> privacyMap = new HashMap<>(16);
        if (!momentIds.isEmpty()) {
            for (Map<String, Object> row : momentLikeMapper.countByMomentIds(momentIds)) {
                likeCounts.put(longValue(row.get("moment_id")), intValue(row.get("cnt")));
            }
            for (Map<String, Object> row : momentCommentMapper.countByMomentIds(momentIds)) {
                commentCounts.put(longValue(row.get("moment_id")), intValue(row.get("cnt")));
            }
            for (Map<String, Object> row : momentPrivacyMapper.findBlockUidsByMomentIds(momentIds)) {
                privacyMap.computeIfAbsent(longValue(row.get("moment_id")), k -> new HashSet<>())
                        .add(longValue(row.get("block_uid")));
            }
        }

        for (FeedTimeline feed : feeds) {
            Moment m = momentMap.get(feed.getMomentId());
            if (m == null) {
                continue;
            }
            if (blockedUids.contains(m.getUid())) {
                continue;
            }
            if (VISIBILITY_RESTRICTED.equals(m.getVisibility())) {
                Set<Long> blockUids = privacyMap.get(m.getMomentId());
                if (blockUids != null && blockUids.contains(uid)) {
                    continue;
                }
            }
            if (m.getShowRange() != null) {
                LocalDateTime cutoff = getRangeCutoff(m.getShowRange());
                if (cutoff != null && m.getCreatedAt().isBefore(cutoff)) {
                    continue;
                }
            }
            Map<String, Object> item = new LinkedHashMap<>(16);
            item.put("momentId", String.valueOf(m.getMomentId()));
            item.put("uid", String.valueOf(m.getUid()));
            item.put("content", m.getContent());
            item.put("media", m.getMedia());
            item.put("visibility", m.getVisibility());
            item.put("showRange", m.getShowRange());
            item.put("createdAt", m.getCreatedAt());
            item.put("likeCount", likeCounts.getOrDefault(m.getMomentId(), 0));
            item.put("commentCount", commentCounts.getOrDefault(m.getMomentId(), 0));
            item.put("isLiked", likedMomentIds.contains(m.getMomentId()));
            result.add(item);
        }
        return result;
    }

    private static Long longValue(Object v) {
        return v instanceof Number n ? n.longValue() : Long.parseLong(v.toString());
    }

    private static Integer intValue(Object v) {
        return v instanceof Number n ? n.intValue() : Integer.parseInt(v.toString());
    }

    /**
     * 获取单条动态详情，含访问控制。被屏蔽的用户查看受限动态时返回404。
     *
     * @param uid      查看者用户ID
     * @param momentId 要获取的动态ID
     * @return 授权访问的动态
     */
    @Override
    public Moment getMomentDetail(Long uid, Long momentId) {
        return checkMomentAccess(momentId, uid);
    }

    /**
     * 获取指定用户的分页动态列表，含可见性检查。
     * 受限动态对被屏蔽的查看者隐藏，但作者自身始终可见。
     *
     * @param viewerUid 查看者用户ID
     * @param targetUid 目标用户ID（要获取其动态的用户）
     * @param beforeId  分页游标动态ID（首页时传null）
     * @param limit     最多返回的动态数
     * @return 过滤后的可见动态列表
     */
    @Override
    public List<Moment> getUserMoments(Long viewerUid, Long targetUid, Long beforeId, int limit) {
        if (isBlocked(viewerUid, targetUid)) {
            return List.of();
        }
        LocalDateTime beforeTime = beforeId != null ? getMomentTime(beforeId) : LocalDateTime.now();
        List<Moment> moments = momentMapper.findByUidPaginated(targetUid, beforeTime, limit);
        boolean isOwner = viewerUid.equals(targetUid);
        List<Moment> filtered = moments.stream()
                .filter(m -> isOwner
                        || !VISIBILITY_RESTRICTED.equals(m.getVisibility())
                        || !momentPrivacyMapper.findBlockUidsByMomentId(m.getMomentId()).contains(viewerUid))
                .toList();
        if (filtered.isEmpty()) {
            return List.of();
        }
        // 使用点赞/评论元数据富化（与feed接口返回相同的字段）
        List<Long> momentIds = filtered.stream().map(Moment::getMomentId).toList();
        Set<Long> likedIds = new HashSet<>(momentLikeMapper.findLikedMomentIds(viewerUid, momentIds));
        Map<Long, Integer> likeCounts = new HashMap<>(16);
        Map<Long, Integer> commentCounts = new HashMap<>(16);
        for (Map<String, Object> row : momentLikeMapper.countByMomentIds(momentIds)) {
            likeCounts.put(longValue(row.get("moment_id")), intValue(row.get("cnt")));
        }
        for (Map<String, Object> row : momentCommentMapper.countByMomentIds(momentIds)) {
            commentCounts.put(longValue(row.get("moment_id")), intValue(row.get("cnt")));
        }
        for (Moment m : filtered) {
            m.setIsLiked(likedIds.contains(m.getMomentId()));
            m.setLikeCount(likeCounts.getOrDefault(m.getMomentId(), 0));
            m.setCommentCount(commentCounts.getOrDefault(m.getMomentId(), 0));
        }
        return filtered;
    }

    /**
     * 对动态点赞。若用户已点赞或无访问权限则拒绝。
     *
     * @param uid      执行点赞的用户ID
     * @param momentId 要点赞的动态ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void likeMoment(Long uid, Long momentId) {
        Moment moment = checkMomentAccess(momentId, uid);
        requireInteractionPermission(moment, uid);

        MomentLike existing = momentLikeMapper.findByMomentAndUid(momentId, uid);
        if (existing != null) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "Already liked");
        }

        MomentLike like = new MomentLike();
        like.setMomentId(momentId);
        like.setUid(uid);
        like.setCreatedAt(LocalDateTime.now());
        try {
            momentLikeMapper.insert(like);
        } catch (DuplicateKeyException e) {
            // 并发点赞绕过了存在性检查；
            // 唯一索引(moment_id, uid)保证只有一条记录，因此按已点赞处理
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "Already liked");
        }
    }

    /**
     * 取消对动态的点赞。若用户未点赞或无访问权限则拒绝。
     *
     * @param uid      取消点赞的用户ID
     * @param momentId 要取消点赞的动态ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlikeMoment(Long uid, Long momentId) {
        checkMomentAccess(momentId, uid);
        MomentLike existing = momentLikeMapper.findByMomentAndUid(momentId, uid);
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "Not liked yet");
        }
        momentLikeMapper.deleteById(existing.getId());
    }

    /**
     * 获取动态的点赞列表。被屏蔽的用户收到404。
     *
     * @param uid      查看者用户ID
     * @param momentId 要获取点赞的动态ID
     * @return 该动态的点赞列表
     */
    @Override
    public List<MomentLike> getLikes(Long uid, Long momentId) {
        checkMomentAccess(momentId, uid);
        return momentLikeMapper.findByMomentId(momentId);
    }

    /**
     * 获取动态的评论列表。被屏蔽的用户收到404。
     *
     * @param uid      查看者用户ID
     * @param momentId 要获取评论的动态ID
     * @return 该动态的评论列表
     */
    @Override
    public List<MomentComment> getComments(Long uid, Long momentId) {
        checkMomentAccess(momentId, uid);
        return momentCommentMapper.findByMomentId(momentId);
    }

    /**
     * 对动态添加评论，可指定回复某个评论者。
     *
     * @param uid        评论者用户ID
     * @param momentId   要评论的动态ID
     * @param replyToUid 被回复的用户ID（顶级评论时传null）
     * @param content    评论文本内容
     * @return 创建的评论
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MomentComment comment(Long uid, Long momentId, Long replyToUid, String content) {
        Moment moment = checkMomentAccess(momentId, uid);
        requireInteractionPermission(moment, uid);
        if (content == null || content.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "Comment content cannot be empty");
        }

        MomentComment comment = new MomentComment();
        comment.setMomentId(momentId);
        comment.setUid(uid);
        comment.setReplyToUid(replyToUid);
        comment.setContent(content);
        comment.setCreatedAt(LocalDateTime.now());
        momentCommentMapper.insert(comment);
        return comment;
    }

    /**
     * 删除评论。仅评论作者或动态作者可以删除。
     *
     * @param uid       请求用户ID
     * @param commentId 要删除的评论ID
     */
    @Override
    public void deleteComment(Long uid, Long commentId) {
        MomentComment comment = momentCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "Comment not found");
        }
        if (!comment.getUid().equals(uid)) {
            Moment moment = momentMapper.selectById(comment.getMomentId());
            if (moment == null || !moment.getUid().equals(uid)) {
                throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Can only delete own comments");
            }
        }
        momentCommentMapper.deleteById(commentId);
    }

    /**
     * 验证动态是否存在、未被删除，且查看者未被屏蔽（对于受限动态）。
     * 成功时返回动态，否则抛出404异常。
     */
    private Moment checkMomentAccess(Long momentId, Long uid) {
        Moment moment = momentMapper.findActiveById(momentId);
        if (moment == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "Moment not found or deleted");
        }
        if (VISIBILITY_RESTRICTED.equals(moment.getVisibility())
                && !moment.getUid().equals(uid)
                && momentPrivacyMapper.findBlockUidsByMomentId(momentId).contains(uid)) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "Moment not found or deleted");
        }
        return moment;
    }

    /**
     * 确保操作用户有权对动态点赞或评论。作者始终可以操作自己的动态；
     * 其他用户必须与作者是双向好友。查看权限对非好友保持开放。
     *
     * @param moment 目标动态（作者为{@code moment.getUid()}）
     * @param uid    操作用户ID
     * @throws BusinessException 若用户非作者且非好友则抛出403
     */
    private void requireInteractionPermission(Moment moment, Long uid) {
        Long authorUid = moment.getUid();
        if (authorUid.equals(uid)) {
            return;
        }
        boolean friends;
        try {
            Map<String, Boolean> res = userFeignClient.checkFriendship(uid, authorUid);
            friends = res != null && Boolean.TRUE.equals(res.get(BusinessConstants.FRIENDSHIP_KEY_FRIENDS));
        } catch (Exception e) {
            log.warn("Friendship check failed for uid={}, authorUid={}: {}", uid, authorUid, e.getMessage());
            throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), "Unable to verify friendship");
        }
        if (!friends) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "Only friends can like or comment on this moment");
        }
    }

    /** 获取指定动态ID的createdAt时间戳，不存在则默认返回当前时间。 */
    private LocalDateTime getMomentTime(Long momentId) {
        Moment m = momentMapper.selectById(momentId);
        return m != null ? m.getCreatedAt() : LocalDateTime.now();
    }

    /** 将showRange标签转换为截止时间戳。 */
    private LocalDateTime getRangeCutoff(String showRange) {
        if (showRange == null) {
            return null;
        }
        return switch (showRange) {
            case "3d" -> LocalDateTime.now().minusDays(3);
            case "30d" -> LocalDateTime.now().minusDays(30);
            case "180d" -> LocalDateTime.now().minusDays(180);
            default -> null;
        };
    }

    private boolean isBlocked(Long uid1, Long uid2) {
        try {
            return userFeignClient.getBlacklistUids(uid1).contains(uid2)
                    || userFeignClient.getBlacklistUids(uid2).contains(uid1);
        } catch (Exception e) {
            return false;
        }
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void archiveFeed() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(BusinessConstants.FEED_ARCHIVE_DAYS);
        int deleted = feedTimelineMapper.delete(
            new LambdaQueryWrapper<FeedTimeline>().lt(FeedTimeline::getCreatedAt, cutoff));
        if (deleted > 0) {
            log.info("Archived {} feed timeline entries older than {} days", deleted, BusinessConstants.FEED_ARCHIVE_DAYS);
        }
    }
}
