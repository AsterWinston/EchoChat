package me.aster.echochat.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.aster.echochat.message.entity.Conversation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 会话CRUD和自定义查询的MyBatis映射器。
 * @author AsterWinston
 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    /**
     * @param uid         所属用户ID
     * @param sessionType 会话类型
     * @param targetId    目标对端/群组ID
     * @return 匹配的会话，或null
     */
    @Select("SELECT * FROM conversation WHERE uid = #{uid} AND session_type = #{sessionType} AND target_id = #{targetId}")
    Conversation findByUidAndTarget(@Param("uid") Long uid,
                                    @Param("sessionType") String sessionType,
                                    @Param("targetId") String targetId);

    /**
     * @param uid 所属用户ID
     * @return 按置顶状态和最后更新时间排序的会话列表
     */
    @Select("SELECT * FROM conversation WHERE uid = #{uid} ORDER BY is_pinned DESC, updated_at DESC")
    List<Conversation> findByUid(@Param("uid") Long uid);

    /**
     * 覆写会话的未读计数快照；由定期任务使用，将实时Redis计数器刷回MySQL。
     *
     * @param uid         所属用户ID
     * @param sessionType 会话类型
     * @param targetId    目标对端/群组ID
     * @param unreadCount Redis中的快照值
     * @return 更新的行数
     */
    @Update("UPDATE conversation SET unread_count = #{unreadCount} WHERE uid = #{uid} AND session_type = #{sessionType} AND target_id = #{targetId}")
    int updateUnreadCount(@Param("uid") Long uid, @Param("sessionType") String sessionType,
                          @Param("targetId") String targetId, @Param("unreadCount") int unreadCount);

    /**
     * @param targetId 群组ID（字符串形式）
     * @param uids     候选成员UID
     * @return 已有该群组会话记录的UID子集
     */
    @Select("<script>SELECT uid FROM conversation WHERE session_type = 'group' AND target_id = #{targetId} " +
            "AND uid IN <foreach collection='uids' item='u' open='(' separator=',' close=')'>#{u}</foreach></script>")
    List<Long> findExistingGroupConversationUids(@Param("targetId") String targetId,
                                                 @Param("uids") List<Long> uids);

    /**
     * 群消息的扇出写入：用一条语句更新所有指定成员的last_msg_id/updated_at，
     * 而非每个成员一条UPDATE。
     *
     * @param targetId  群组ID（字符串形式）
     * @param msgId     最新消息ID
     * @param updatedAt 更新时间戳
     * @param uids      需要更新的成员UID
     * @return 更新的行数
     */
    @Update("<script>UPDATE conversation SET last_msg_id = #{msgId}, updated_at = #{updatedAt} " +
            "WHERE session_type = 'group' AND target_id = #{targetId} " +
            "AND uid IN <foreach collection='uids' item='u' open='(' separator=',' close=')'>#{u}</foreach></script>")
    int batchTouchGroupConversations(@Param("targetId") String targetId,
                                     @Param("msgId") Long msgId,
                                     @Param("updatedAt") java.time.LocalDateTime updatedAt,
                                     @Param("uids") List<Long> uids);

    /**
     * 新创建群组会话行的多行插入。
     *
     * @param items 要插入的会话行
     * @return 插入的行数
     */
    @Insert("<script>INSERT INTO conversation (id, uid, session_type, target_id, last_msg_id, unread_count, is_pinned, created_at, updated_at) VALUES " +
            "<foreach collection='items' item='c' separator=','>" +
            "(#{c.id}, #{c.uid}, #{c.sessionType}, #{c.targetId}, #{c.lastMsgId}, #{c.unreadCount}, #{c.isPinned}, #{c.createdAt}, #{c.updatedAt})" +
            "</foreach></script>")
    int batchInsert(@Param("items") List<Conversation> items);
}