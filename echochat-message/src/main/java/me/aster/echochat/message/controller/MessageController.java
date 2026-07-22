package me.aster.echochat.message.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import me.aster.echochat.common.constant.BusinessConstants;
import me.aster.echochat.common.context.UserContext;
import me.aster.echochat.common.result.Result;
import me.aster.echochat.message.dto.ForwardGroupMessageRequest;
import me.aster.echochat.message.dto.ForwardMessageRequest;
import me.aster.echochat.message.dto.PinMessageRequest;
import me.aster.echochat.message.dto.ReplyMessageRequest;
import me.aster.echochat.message.dto.SendGroupMessageRequest;
import me.aster.echochat.message.dto.SendMessageRequest;
import me.aster.echochat.message.entity.Message;
import me.aster.echochat.message.service.ConversationService;
import me.aster.echochat.message.service.MessageService;
import me.aster.echochat.message.service.impl.MessageReadService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST控制器，提供消息相关端点，包括发送、历史记录、撤回、
 * 删除、转发、回复、置顶和会话管理。
 * @author AsterWinston
 */
@Validated
@RestController
@RequestMapping("/api/message")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final ConversationService conversationService;
    private final MessageReadService messageReadService;

    /**
     * @param req 包含toUid、msgType和content的请求
     * @return 发送的{@link Message}
     */
    @PostMapping("/send")
    public Result<Message> sendMessage(@Valid @RequestBody SendMessageRequest req) {
        Long uid = UserContext.get();
        return Result.ok(messageService.sendMessage(uid, req.getToUid(), req.getMsgType(), req.getContent()));
    }

    /**
     * @param targetUid 会话对端
     * @param beforeSeq 可选的分页游标
     * @param limit     最大返回消息数，默认20
     * @return 聊天历史{@link Message}列表
     */
    @GetMapping("/history/{targetUid}")
    public Result<List<Message>> getHistory(@PathVariable Long targetUid,
                                            @RequestParam(required = false) Long beforeSeq,
                                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        Long uid = UserContext.get();
        return Result.ok(messageService.getHistory(uid, targetUid, beforeSeq, limit));
    }

    /**
     * @return 当前用户的会话摘要列表
     */
    @GetMapping("/conversations")
    public Result<List<Map<String, Object>>> getConversations() {
        Long uid = UserContext.get();
        return Result.ok(conversationService.getConversations(uid));
    }

    /**
     * @param msgId 要撤回的消息ID
     * @return 撤回后的{@link Message}
     */
    @PutMapping("/recall/{msgId}")
    public Result<Message> recallMessage(@PathVariable Long msgId) {
        Long uid = UserContext.get();
        return Result.ok(messageService.recallMessage(uid, msgId));
    }

    /**
     * @param msgId 要删除的消息ID
     * @return 空的成功结果
     */
    @DeleteMapping("/{msgId}")
    public Result<Void> deleteMessage(@PathVariable Long msgId) {
        Long uid = UserContext.get();
        messageService.deleteMessage(uid, msgId);
        return Result.ok();
    }

    /**
     * @param req 包含toUid和要转发的msgId的请求
     * @return 转发后的{@link Message}
     */
    @PostMapping("/forward")
    public Result<Message> forwardMessage(@Valid @RequestBody ForwardMessageRequest req) {
        Long uid = UserContext.get();
        return Result.ok(messageService.forwardMessage(uid, req.getToUid(), req.getMsgId()));
    }

    /**
     * @param req 包含gid和要转发的msgId的请求
     * @return 转发后的群{@link Message}
     */
    @PostMapping("/forward/group")
    public Result<Message> forwardGroupMessage(@Valid @RequestBody ForwardGroupMessageRequest req) {
        Long uid = UserContext.get();
        return Result.ok(messageService.forwardGroupMessage(uid, req.getGid(), req.getMsgId()));
    }

    /**
     * @param req 包含toUid、replyToMsgId和content的请求
     * @return 回复的{@link Message}
     */
    @PostMapping("/reply")
    public Result<Message> replyMessage(@Valid @RequestBody ReplyMessageRequest req) {
        Long uid = UserContext.get();
        return Result.ok(messageService.replyMessage(uid, req.getToUid(), req.getReplyToMsgId(), req.getContent()));
    }

    /**
     * @param req 包含gid、msgType、content和可选replyToMsgId的请求
     * @return 发送的群{@link Message}
     */
    @PostMapping("/send/group")
    public Result<Message> sendGroupMessage(@Valid @RequestBody SendGroupMessageRequest req) {
        Long uid = UserContext.get();
        return Result.ok(messageService.sendGroupMessage(uid, req.getGid(), req.getMsgType(),
                req.getContent(), req.getReplyToMsgId()));
    }

    /**
     * @param gid       群组ID
     * @param beforeSeq 可选的分页游标
     * @param limit     最大返回消息数，默认20
     * @return 群聊历史{@link Message}列表
     */
    @GetMapping("/group/{gid}/history")
    public Result<List<Message>> getGroupHistory(@PathVariable Long gid,
                                                  @RequestParam(required = false) Long beforeSeq,
                                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        Long uid = UserContext.get();
        return Result.ok(messageService.getGroupHistory(uid, gid, beforeSeq, limit));
    }

    /**
     * @param msgId 要置顶的消息ID
     * @param req   包含targetUid和可选contentSummary的请求
     * @return 空的成功结果
     */
    @PutMapping("/pin/{msgId}")
    public Result<Void> pinMessage(@PathVariable Long msgId, @Valid @RequestBody PinMessageRequest req) {
        Long uid = UserContext.get();
        String contentSummary = req.getContentSummary() != null ? req.getContentSummary() : "";
        messageService.pinMessage(uid, req.getTargetUid(), msgId, contentSummary);
        return Result.ok();
    }

    /**
     * @param targetUid 要标记为已读的会话对端
     * @return 空的成功结果
     */
    @PutMapping("/read/{targetUid}")
    public Result<Void> markAsRead(@PathVariable Long targetUid) {
        Long uid = UserContext.get();
        conversationService.markAsRead(uid, BusinessConstants.SESSION_TYPE_SINGLE, String.valueOf(targetUid));
        return Result.ok();
    }

    /**
     * @param gid 要标记为已读的群组ID
     * @return 空的成功结果
     */
    @PutMapping("/group/{gid}/read")
    public Result<Void> markGroupAsRead(@PathVariable Long gid) {
        Long uid = UserContext.get();
        conversationService.markAsRead(uid, BusinessConstants.SESSION_TYPE_GROUP, String.valueOf(gid));
        return Result.ok();
    }

    /**
     * @param targetUid 会话对端
     * @param body      包含置顶标志的请求体
     * @return 空的成功结果
     */
    @PutMapping("/conversation/{targetUid}/pin")
    public Result<Void> pinConversation(@PathVariable Long targetUid, @RequestBody Map<String, Boolean> body) {
        Long uid = UserContext.get();
        boolean pinned = Boolean.TRUE.equals(body.get("pinned"));
        conversationService.pinConversation(uid, BusinessConstants.SESSION_TYPE_SINGLE, String.valueOf(targetUid), pinned);
        return Result.ok();
    }

    @PutMapping("/conversation/{targetUid}/dnd")
    public Result<Void> setConversationDnd(@PathVariable Long targetUid, @RequestBody Map<String, Boolean> body) {
        Long uid = UserContext.get();
        conversationService.setDnd(uid, BusinessConstants.SESSION_TYPE_SINGLE, String.valueOf(targetUid), Boolean.TRUE.equals(body.get("dnd")));
        return Result.ok();
    }

    /**
     * @param gid  要置顶/取消置顶的群组ID
     * @param body 包含置顶标志的请求体
     * @return 空的成功结果
     */
    @PutMapping("/group/{gid}/pin")
    public Result<Void> pinGroupConversation(@PathVariable Long gid, @RequestBody Map<String, Boolean> body) {
        Long uid = UserContext.get();
        boolean pinned = Boolean.TRUE.equals(body.get("pinned"));
        conversationService.pinConversation(uid, BusinessConstants.SESSION_TYPE_GROUP, String.valueOf(gid), pinned);
        return Result.ok();
    }

    /**
     * @param targetUid 要删除的会话对端
     * @return 空的成功结果
     */
    @DeleteMapping("/conversation/{targetUid}")
    public Result<Void> deleteConversation(@PathVariable Long targetUid) {
        Long uid = UserContext.get();
        conversationService.deleteConversation(uid, BusinessConstants.SESSION_TYPE_SINGLE, String.valueOf(targetUid));
        return Result.ok();
    }

    /**
     * @param gid 要删除会话的群组ID
     * @return 空的成功结果
     */
    @DeleteMapping("/group/{gid}")
    public Result<Void> deleteGroupConversation(@PathVariable Long gid) {
        Long uid = UserContext.get();
        conversationService.deleteConversation(uid, BusinessConstants.SESSION_TYPE_GROUP, String.valueOf(gid));
        return Result.ok();
    }

    @GetMapping("/{msgId}/read/status")
    public Result<Map<String, Object>> getReadStatus(@PathVariable Long msgId) {
        Long uid = UserContext.get();
        return Result.ok(messageReadService.getGroupReadStatus(msgId, uid));
    }

    @GetMapping("/pinned/{sessionType}/{targetId}")
    public Result<List<Map<String, Object>>> getPinnedMessages(@PathVariable String sessionType, @PathVariable Long targetId) {
        Long uid = UserContext.get();
        return Result.ok(messageService.getPinnedMessages(uid, sessionType, String.valueOf(targetId)));
    }

    @DeleteMapping("/pin/{msgId}")
    public Result<Void> unpinMessage(@PathVariable Long msgId) {
        Long uid = UserContext.get();
        messageService.unpinMessage(uid, msgId);
        return Result.ok();
    }

    @GetMapping("/sync/{sessionType}/{targetId}")
    public Result<List<Message>> syncMessages(@PathVariable String sessionType,
                                               @PathVariable Long targetId,
                                               @RequestParam(defaultValue = "0") Long afterSeq) {
        Long uid = UserContext.get();
        return Result.ok(messageService.getMessagesAfterSeq(uid, sessionType, String.valueOf(targetId), afterSeq));
    }

    @GetMapping("/context/{msgId}")
    public Result<List<Message>> getMessageContext(@PathVariable Long msgId,
                                                    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Long uid = UserContext.get();
        return Result.ok(messageService.getMessageContext(uid, msgId, size));
    }
}