package me.aster.echochat.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.aster.echochat.message.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

/**
 * 消息持久化的MyBatis映射器，提供历史记录、序号和未读跟踪的自定义查询。
 * @author AsterWinston
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /**
     * 单聊历史记录查询，使用两个UNION ALL分支，使每个分支可以利用
     * (session_type, from_uid, to_id, seq)索引进行范围扫描，而非在OR条件下退化为全表扫描。
     *
     * @param uid1       第一位会话参与者
     * @param uid2       第二位会话参与者
     * @param uid1Str    第一位参与者UID的字符串形式
     * @param uid2Str    第二位参与者UID的字符串形式
     * @param beforeSeq  分页游标序号（可为null）
     * @param limit      最大返回结果数
     * @param currentUid 当前用户ID（用于排除已删除消息）
     * @return 按序号降序排列的会话历史
     */
    @Select("SELECT * FROM (" +
            "(SELECT m.* FROM message m " +
            " LEFT JOIN message_deletion md ON md.msg_id = m.msg_id AND md.uid = #{currentUid} " +
            " WHERE m.session_type = 'single' AND m.from_uid = #{uid1} AND m.to_id = #{uid2Str} " +
            " AND (m.seq < #{beforeSeq} OR #{beforeSeq} IS NULL) AND md.id IS NULL " +
            " ORDER BY m.seq DESC LIMIT #{limit}) " +
            "UNION ALL " +
            "(SELECT m.* FROM message m " +
            " LEFT JOIN message_deletion md ON md.msg_id = m.msg_id AND md.uid = #{currentUid} " +
            " WHERE m.session_type = 'single' AND m.from_uid = #{uid2} AND m.to_id = #{uid1Str} " +
            " AND (m.seq < #{beforeSeq} OR #{beforeSeq} IS NULL) AND md.id IS NULL " +
            " ORDER BY m.seq DESC LIMIT #{limit})" +
            ") t ORDER BY t.seq DESC LIMIT #{limit}")
    List<Message> findHistory(@Param("uid1") Long uid1,
                              @Param("uid2") Long uid2,
                              @Param("uid1Str") String uid1Str,
                              @Param("uid2Str") String uid2Str,
                              @Param("beforeSeq") Long beforeSeq,
                              @Param("limit") int limit,
                              @Param("currentUid") Long currentUid);

    /**
     * 获取指定单聊会话中的最大消息序号。
     *
     * @param uid1    第一位会话参与者
     * @param uid2    第二位会话参与者
     * @param uid1Str 第一位参与者UID的字符串形式（用于varchar类型to_id列）
     * @param uid2Str 第二位参与者UID的字符串形式（用于varchar类型to_id列）
     * @return 该会话中的最大序号，或0
     */
    @Select("SELECT COALESCE(MAX(t.seq), 0) FROM (" +
            "(SELECT MAX(seq) AS seq FROM message " +
            " WHERE session_type = 'single' AND from_uid = #{uid1} AND to_id = #{uid2Str}) " +
            "UNION ALL " +
            "(SELECT MAX(seq) AS seq FROM message " +
            " WHERE session_type = 'single' AND from_uid = #{uid2} AND to_id = #{uid1Str})" +
            ") t")
    Long getMaxSeq(@Param("uid1") Long uid1, @Param("uid2") Long uid2,
                   @Param("uid1Str") String uid1Str, @Param("uid2Str") String uid2Str);

    /**
     * 查找被撤回消息之前的最近一条未撤回消息。
     *
     * @param sessionType   会话类型
     * @param uid           第一位参与者
     * @param peerUid       第二位参与者
     * @param uidStr        第一位参与者UID的字符串形式
     * @param peerUidStr    第二位参与者UID的字符串形式
     * @param recalledMsgId 被撤回的消息ID
     * @return 在被撤回消息之前的最近一条未撤回消息，或null
     */
    @Select("SELECT * FROM (" +
            "(SELECT m.* FROM message m " +
            " WHERE m.session_type = #{sessionType} AND m.from_uid = #{uid} AND m.to_id = #{peerUidStr} " +
            " AND m.msg_id != #{recalledMsgId} AND (m.is_recalled IS NULL OR m.is_recalled = 0) " +
            " AND m.seq < (SELECT seq FROM message WHERE msg_id = #{recalledMsgId}) " +
            " ORDER BY m.seq DESC LIMIT 1) " +
            "UNION ALL " +
            "(SELECT m.* FROM message m " +
            " WHERE m.session_type = #{sessionType} AND m.from_uid = #{peerUid} AND m.to_id = #{uidStr} " +
            " AND m.msg_id != #{recalledMsgId} AND (m.is_recalled IS NULL OR m.is_recalled = 0) " +
            " AND m.seq < (SELECT seq FROM message WHERE msg_id = #{recalledMsgId}) " +
            " ORDER BY m.seq DESC LIMIT 1)" +
            ") t ORDER BY t.seq DESC LIMIT 1")
    Message findPrevNonRecalled(@Param("sessionType") String sessionType,
                                @Param("uid") Long uid,
                                @Param("peerUid") Long peerUid,
                                @Param("uidStr") String uidStr,
                                @Param("peerUidStr") String peerUidStr,
                                @Param("recalledMsgId") Long recalledMsgId);

    /**
     * 获取指定群组的消息最大序号。
     *
     * @param gid 群组ID
     * @return 群消息的最大序号，或0
     */
    @Select("SELECT COALESCE(MAX(seq), 0) FROM message " +
            "WHERE session_type = 'group' AND to_id = #{gid}")
    Long getMaxGroupSeq(@Param("gid") Long gid);

    /**
     * 根据消息ID查询消息。
     *
     * @param msgId 消息ID
     * @return 匹配的消息，或null
     */
    @Select("SELECT * FROM message WHERE msg_id = #{msgId}")
    Message findByMsgId(@Param("msgId") Long msgId);

    /**
     * 查询到指定序号为止的未读消息列表。
     *
     * @param fromUid 发送者
     * @param toId    接收者ID
     * @param toSeq   序号的包含上限
     * @return 到指定序号为止的未读消息（status < 2）
     */
    @Select("SELECT * FROM message WHERE from_uid = #{fromUid} AND to_id = #{toId} " +
             "AND seq <= #{toSeq} AND (status IS NULL OR status < 2) " +
             "ORDER BY seq ASC")
    List<Message> findUnreadMessages(@Param("fromUid") Long fromUid,
                                      @Param("toId") String toId,
                                      @Param("toSeq") Long toSeq);

    /**
     * 查找指定群组中用户未读的消息ID列表。
     *
     * @param gid 群组ID
     * @param uid 用户ID
     * @return 未读消息ID列表，按序号降序排列
     */
    @Select("SELECT m.msg_id FROM message m WHERE m.session_type = 'group' AND m.to_id = #{gid} " +
            "AND NOT EXISTS (SELECT 1 FROM message_read mr WHERE mr.msg_id = m.msg_id AND mr.uid = #{uid}) " +
            "ORDER BY m.seq DESC")
    List<Long> findUnreadGroupMessageIds(@Param("gid") String gid, @Param("uid") Long uid);

    /**
     * 查询群聊历史消息，按序号分页。
     *
     * @param gid        群组ID
     * @param beforeSeq  分页游标序号（可为null）
     * @param limit      最大返回结果数
     * @param currentUid 当前用户ID（用于排除已删除消息）
     * @return 按序号降序排列的群聊历史
     */
    @Select("SELECT m.* FROM message m " +
            "LEFT JOIN message_deletion md ON md.msg_id = m.msg_id AND md.uid = #{currentUid} " +
            "WHERE m.session_type = 'group' AND m.to_id = #{gid} " +
            "AND (m.seq < #{beforeSeq} OR #{beforeSeq} IS NULL) " +
            "AND md.id IS NULL " +
            "ORDER BY m.seq DESC LIMIT #{limit}")
    List<Message> findGroupHistory(@Param("gid") Long gid,
                                   @Param("beforeSeq") Long beforeSeq,
                                   @Param("limit") int limit,
                                   @Param("currentUid") Long currentUid);

    /**
     * 将会话中由对端发送给阅读者的所有消息标记为已读（status=2）。
     *
     * @param sessionType  会话类型
     * @param peerUid      消息发送者的UID
     * @param readerUidStr 阅读者的UID（字符串形式，用于to_id列）
     * @return 更新的行数
     */
    @Update("UPDATE message SET status = 2 WHERE session_type = #{sessionType} " +
            "AND to_id = #{readerUidStr} AND from_uid = #{peerUid} AND (status IS NULL OR status < 2)")
    int markMessagesAsRead(@Param("sessionType") String sessionType,
                           @Param("peerUid") Long peerUid,
                           @Param("readerUidStr") String readerUidStr);

    /**
     * 查询指定发送者发给该用户的最大未读序号。
     *
     * @param senderUid 发送者UID
     * @param readerUid 阅读者UID（字符串形式）
     * @return 最大未读序号，或0
     */
    @Select("SELECT COALESCE(MAX(seq), 0) FROM message WHERE session_type = 'single' " +
            "AND from_uid = #{senderUid} AND to_id = #{readerUid} AND (status IS NULL OR status < 2)")
    Long findMaxUnreadSeq(@Param("senderUid") Long senderUid, @Param("readerUid") String readerUid);

    /**
     * 查询指定序号之后的消息（按序号升序）。
     *
     * @param sessionType 会话类型
     * @param targetId    目标ID
     * @param afterSeq    起始序号
     * @param limit       最大返回数
     * @param currentUid  当前用户ID（用于排除已删除）
     * @return 按序号升序排列的消息列表
     */
    @Select("SELECT m.* FROM message m " +
            "LEFT JOIN message_deletion md ON md.msg_id = m.msg_id AND md.uid = #{currentUid} " +
            "WHERE m.session_type = #{sessionType} AND m.to_id = #{targetId} " +
            "AND m.seq > #{afterSeq} " +
            "AND md.id IS NULL " +
            "ORDER BY m.seq ASC LIMIT #{limit}")
    List<Message> findMessagesAfterSeq(@Param("sessionType") String sessionType,
                                        @Param("targetId") String targetId,
                                        @Param("afterSeq") Long afterSeq,
                                        @Param("limit") int limit,
                                        @Param("currentUid") Long currentUid);

    /**
     * 查询指定序号之前的消息（按序号降序）。
     *
     * @param sessionType 会话类型
     * @param targetId    目标ID
     * @param beforeSeq   结束序号
     * @param limit       最大返回数
     * @param currentUid  当前用户ID（用于排除已删除）
     * @return 按序号降序排列的消息列表
     */
    @Select("SELECT m.* FROM message m " +
            "LEFT JOIN message_deletion md ON md.msg_id = m.msg_id AND md.uid = #{currentUid} " +
            "WHERE m.session_type = #{sessionType} AND m.to_id = #{targetId} " +
            "AND m.seq < #{beforeSeq} " +
            "AND md.id IS NULL " +
            "ORDER BY m.seq DESC LIMIT #{limit}")
    List<Message> findMessagesBeforeSeq(@Param("sessionType") String sessionType,
                                         @Param("targetId") String targetId,
                                         @Param("beforeSeq") Long beforeSeq,
                                         @Param("limit") int limit,
                                         @Param("currentUid") Long currentUid);
}