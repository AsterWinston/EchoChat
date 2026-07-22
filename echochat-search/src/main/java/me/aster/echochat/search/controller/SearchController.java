package me.aster.echochat.search.controller;

import lombok.RequiredArgsConstructor;
import me.aster.echochat.common.context.UserContext;
import me.aster.echochat.common.result.Result;
import me.aster.echochat.search.entity.GroupDocument;
import me.aster.echochat.search.entity.MessageDocument;
import me.aster.echochat.search.entity.UserDocument;
import me.aster.echochat.search.service.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST控制器，提供消息、用户和群组的搜索接口。
 * @author AsterWinston
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /**
     * 在全局范围内搜索当前用户的所有会话中的消息。
     *
     * @param q 搜索关键词
     * @return 包含匹配的{@link MessageDocument}列表的{@link Result}
     */
    @GetMapping("/messages")
    public Result<List<MessageDocument>> searchMessages(@RequestParam String q) {
        return Result.ok(searchService.searchMessages(UserContext.get(), q));
    }

    /**
     * 在指定的一对一会话中搜索与目标用户之间的消息。
     *
     * @param targetUid 对方参与者的用户ID
     * @param q         搜索关键词
     * @return 包含匹配的{@link MessageDocument}列表的{@link Result}
     */
    @GetMapping("/messages/with/{targetUid}")
    public Result<List<MessageDocument>> searchMessagesWithUser(
            @PathVariable Long targetUid, @RequestParam String q) {
        return Result.ok(searchService.searchMessagesWithUser(UserContext.get(), targetUid, q));
    }

    /**
     * 按昵称或邮箱搜索用户。
     *
     * @param q 搜索关键词
     * @return 包含匹配的{@link UserDocument}列表的{@link Result}
     */
    @GetMapping("/users")
    public Result<List<UserDocument>> searchUsers(@RequestParam String q) {
        return Result.ok(searchService.searchUsers(q));
    }

    @GetMapping("/groups")
    public Result<List<GroupDocument>> searchGroups(@RequestParam String q) {
        return Result.ok(searchService.searchGroups(q));
    }
}
