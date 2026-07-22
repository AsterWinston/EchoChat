package me.aster.echochat.moment.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import me.aster.echochat.common.context.UserContext;
import me.aster.echochat.common.result.Result;
import me.aster.echochat.moment.dto.CommentRequest;
import me.aster.echochat.moment.dto.PublishMomentRequest;
import me.aster.echochat.moment.entity.Moment;
import me.aster.echochat.moment.entity.MomentComment;
import me.aster.echochat.moment.entity.MomentLike;
import me.aster.echochat.moment.service.MomentService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST控制器，提供动态/时间线接口：发布、删除、时间线获取、点赞、评论以及用户动态浏览。
 * @author AsterWinston
 */
@Validated
@RestController
@RequestMapping("/api/moment")
@RequiredArgsConstructor
public class MomentController {

    private final MomentService momentService;

    /** 发布新动态，支持可选的媒体、可见性控制和展示时间范围。 */
    @PostMapping
    public Result<Moment> publish(@Valid @RequestBody PublishMomentRequest req) {
        Long uid = UserContext.get();
        String visibility = req.getVisibility() != null ? req.getVisibility() : "public";
        return Result.ok(momentService.publish(uid, req.getContent(), req.getMedia(),
                visibility, req.getBlockUids(), req.getShowRange()));
    }

    /** 删除自己的动态，并级联删除点赞/评论/时间线条目。 */
    @DeleteMapping("/{momentId}")
    public Result<Void> deleteMoment(@PathVariable Long momentId) {
        Long uid = UserContext.get();
        momentService.deleteMoment(uid, momentId);
        return Result.ok();
    }

    /** 获取用户的时间线动态，含隐私过滤和分页结果。 */
    @GetMapping("/feed")
    public Result<List<Map<String, Object>>> getFeed(@RequestParam(required = false) Long beforeId,
                                                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        Long uid = UserContext.get();
        return Result.ok(momentService.getFeed(uid, beforeId, limit));
    }

    /** 获取单条动态详情；被屏蔽的用户查看受限动态时返回404。 */
    @GetMapping("/{momentId}")
    public Result<Moment> getMomentDetail(@PathVariable Long momentId) {
        Long uid = UserContext.get();
        return Result.ok(momentService.getMomentDetail(uid, momentId));
    }

    /** 获取目标用户的分页动态列表。 */
    @GetMapping("/user/{targetUid}")
    public Result<List<Moment>> getUserMoments(@PathVariable Long targetUid,
                                                @RequestParam(required = false) Long beforeId,
                                                @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        Long uid = UserContext.get();
        return Result.ok(momentService.getUserMoments(uid, targetUid, beforeId, limit));
    }

    /** 点赞动态；重复点赞或无访问权限时拒绝。 */
    @PostMapping("/{momentId}/like")
    public Result<Void> likeMoment(@PathVariable Long momentId) {
        Long uid = UserContext.get();
        momentService.likeMoment(uid, momentId);
        return Result.ok();
    }

    /** 取消之前点的赞。 */
    @DeleteMapping("/{momentId}/like")
    public Result<Void> unlikeMoment(@PathVariable Long momentId) {
        Long uid = UserContext.get();
        momentService.unlikeMoment(uid, momentId);
        return Result.ok();
    }

    /** 获取某条动态的点赞列表。 */
    @GetMapping("/{momentId}/likes")
    public Result<List<MomentLike>> getLikes(@PathVariable Long momentId) {
        Long uid = UserContext.get();
        return Result.ok(momentService.getLikes(uid, momentId));
    }

    /** 添加评论，可指定回复某个评论者。 */
    @PostMapping("/{momentId}/comment")
    public Result<MomentComment> comment(@PathVariable Long momentId, @Valid @RequestBody CommentRequest req) {
        Long uid = UserContext.get();
        return Result.ok(momentService.comment(uid, momentId, req.getReplyToUid(), req.getContent()));
    }

    /** 删除评论；仅评论作者或动态作者可以删除。 */
    @DeleteMapping("/comment/{commentId}")
    public Result<Void> deleteComment(@PathVariable Long commentId) {
        Long uid = UserContext.get();
        momentService.deleteComment(uid, commentId);
        return Result.ok();
    }

    /** 获取某条动态的评论列表。 */
    @GetMapping("/{momentId}/comments")
    public Result<List<MomentComment>> getComments(@PathVariable Long momentId) {
        Long uid = UserContext.get();
        return Result.ok(momentService.getComments(uid, momentId));
    }
}
