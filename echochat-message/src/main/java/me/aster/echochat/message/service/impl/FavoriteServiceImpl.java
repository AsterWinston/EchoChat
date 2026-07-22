package me.aster.echochat.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.aster.echochat.common.exception.BusinessException;
import me.aster.echochat.common.result.ResultCode;
import me.aster.echochat.message.entity.Favorite;
import me.aster.echochat.message.entity.Message;
import me.aster.echochat.message.mapper.FavoriteMapper;
import me.aster.echochat.message.mapper.MessageMapper;
import me.aster.echochat.message.service.FavoriteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link FavoriteService}的实现，处理消息收藏和取消收藏，
 * 包含存在性和所有权验证。
 * @author AsterWinston
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final MessageMapper messageMapper;

    /**
     * @param uid   收藏消息的用户
     * @param msgId 要收藏的消息ID
     * @return 创建的{@link Favorite}记录
     * @throws BusinessException 如果消息不存在或已经收藏过
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Favorite addFavorite(Long uid, Long msgId) {
        Message message = messageMapper.findByMsgId(msgId);
        if (message == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "Message not found");
        }

        Favorite existing = favoriteMapper.findByUidAndMsgId(uid, msgId);
        if (existing != null) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "Message already favorited");
        }

        Favorite favorite = new Favorite();
        favorite.setUid(uid);
        favorite.setMsgId(msgId);
        favorite.setMsgType(message.getMsgType());
        favorite.setMsgSummary(message.getContent());
        favorite.setCollectedAt(LocalDateTime.now());
        favoriteMapper.insert(favorite);

        log.info("User {} favorited message {}", uid, msgId);
        return favorite;
    }

    /**
     * @param uid   拥有该收藏的用户
     * @param msgId 要取消收藏的消息ID
     * @throws BusinessException 如果收藏不存在或不属于该用户
     */
    @Override
    public void removeFavorite(Long uid, Long msgId) {
        Favorite favorite = favoriteMapper.findByUidAndMsgId(uid, msgId);
        if (favorite == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "Favorite record not found");
        }

        favoriteMapper.deleteById(favorite.getId());
        log.info("User {} removed favorite for message {}", uid, msgId);
    }

    /**
     * @param uid    拥有这些收藏的用户
     * @param msgIds 要取消收藏的消息ID列表
     */
    @Override
    public void removeFavorites(Long uid, List<Long> msgIds) {
        if (msgIds == null || msgIds.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<Favorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Favorite::getUid, uid)
               .in(Favorite::getMsgId, msgIds);
        favoriteMapper.delete(wrapper);

        log.info("User {} batch-removed {} favorites", uid, msgIds.size());
    }

    /**
     * @param uid     用户ID
     * @param msgType 可选的消息内容类型筛选，null表示所有类型
     * @return 带内容预览的收藏摘要列表
     */
    @Override
    public List<Map<String, Object>> getFavorites(Long uid, String msgType) {
        List<Favorite> favorites;
        if (msgType != null && !msgType.isBlank()) {
            favorites = favoriteMapper.findByUidAndMsgType(uid, msgType);
        } else {
            favorites = favoriteMapper.findByUid(uid);
        }

        if (favorites.isEmpty()) {
            return List.of();
        }
        List<Long> msgIds = favorites.stream().map(Favorite::getMsgId).toList();
        List<Message> messages = messageMapper.selectBatchIds(msgIds);
        Map<Long, Message> msgMap = new HashMap<>(16);
        for (Message m : messages) {
            if (m != null) {
                msgMap.put(m.getMsgId(), m);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Favorite fav : favorites) {
            Map<String, Object> entry = new HashMap<>(16);
            entry.put("id", fav.getId());
            entry.put("msgId", String.valueOf(fav.getMsgId()));
            entry.put("msgType", fav.getMsgType());
            entry.put("msgSummary", fav.getMsgSummary());

            Message message = msgMap.get(fav.getMsgId());
            if (message != null) {
                entry.put("content", message.getContent());
                entry.put("fromUid", String.valueOf(message.getFromUid()));
            } else {
                entry.put("content", null);
                entry.put("fromUid", null);
            }

            entry.put("collectedAt", fav.getCollectedAt());
            result.add(entry);
        }

        return result;
    }
}