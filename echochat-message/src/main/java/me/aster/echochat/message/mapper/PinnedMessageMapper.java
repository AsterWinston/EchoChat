package me.aster.echochat.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.aster.echochat.message.entity.PinnedMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 置顶消息持久化的MyBatis映射器。
 * @author AsterWinston
 */
@Mapper
public interface PinnedMessageMapper extends BaseMapper<PinnedMessage> {

    /**
     * 根据会话类型、目标ID和消息ID查询置顶消息。
     *
     * @param sessionType 会话类型
     * @param targetId    目标对端/群组ID
     * @param msgId       消息ID
     * @return 匹配的置顶消息，或null
     */
    @Select("SELECT * FROM pinned_message WHERE session_type = #{sessionType} AND target_id = #{targetId} AND msg_id = #{msgId}")
    PinnedMessage findBySessionAndMsg(@Param("sessionType") String sessionType,
                                      @Param("targetId") String targetId,
                                      @Param("msgId") Long msgId);

    /**
     * 查询指定会话的所有置顶消息，按置顶时间排序。
     *
     * @param sessionType 会话类型
     * @param targetId    目标ID
     * @return 置顶消息列表
     */
    @Select("SELECT * FROM pinned_message WHERE session_type = #{sessionType} AND target_id = #{targetId} ORDER BY pinned_at ASC")
    List<PinnedMessage> findBySession(@Param("sessionType") String sessionType,
                                       @Param("targetId") String targetId);
}