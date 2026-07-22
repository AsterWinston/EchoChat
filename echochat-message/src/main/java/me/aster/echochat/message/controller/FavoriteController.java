package me.aster.echochat.message.controller;

import lombok.RequiredArgsConstructor;
import me.aster.echochat.common.context.UserContext;
import me.aster.echochat.common.result.Result;
import me.aster.echochat.message.entity.Favorite;
import me.aster.echochat.message.service.FavoriteService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/**
 * REST控制器，提供收藏（消息收藏）端点，包括添加、
 * 移除、批量删除和带可选类型筛选的列表。
 * @author AsterWinston
 */
@RestController
@RequestMapping("/api/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    /**
     * 收藏一条消息。
     *
     * @param body 包含要收藏的msgId的请求体
     * @return 创建的{@link Favorite}记录
     */
    @PostMapping
    public Result<Favorite> addFavorite(@RequestBody Map<String, Object> body) {
        Long msgId = Long.valueOf(String.valueOf(body.get("msgId")));
        return Result.ok(favoriteService.addFavorite(UserContext.get(), msgId));
    }

    /**
     * 移除单条收藏（取消收藏消息）。
     *
     * @param msgId 要取消收藏的消息ID
     * @return 空的成功结果
     */
    @DeleteMapping("/{msgId}")
    public Result<Void> removeFavorite(@PathVariable Long msgId) {
        favoriteService.removeFavorite(UserContext.get(), msgId);
        return Result.ok();
    }

    /**
     * 批量移除多条收藏。
     *
     * @param body 包含要取消收藏的msgId列表的请求体
     * @return 空的成功结果
     */
    @DeleteMapping("/batch")
    public Result<Void> removeFavorites(@RequestBody Map<String, List<Long>> body) {
        favoriteService.removeFavorites(UserContext.get(), body.get("msgIds"));
        return Result.ok();
    }

    /**
     * 列出当前用户的收藏，可按消息类型筛选。
     *
     * @param type 可选的消息内容类型筛选（如TEXT、IMAGE）
     * @return 带内容预览的收藏摘要列表
     */
    @GetMapping
    public Result<List<Map<String, Object>>> getFavorites(@RequestParam(required = false) String type) {
        return Result.ok(favoriteService.getFavorites(UserContext.get(), type));
    }
}