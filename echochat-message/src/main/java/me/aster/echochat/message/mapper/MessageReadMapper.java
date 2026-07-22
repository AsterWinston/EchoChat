package me.aster.echochat.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.aster.echochat.message.entity.MessageRead;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * {@link MessageRead}的MyBatis-Plus映射器，提供按消息ID查询已读记录
 * 和幂等插入已读跟踪的功能。
 * @author AsterWinston
 */
@Mapper
public interface MessageReadMapper extends BaseMapper<MessageRead> {

    @Select("SELECT * FROM message_read WHERE msg_id = #{msgId}")
    List<MessageRead> findByMsgId(@Param("msgId") Long msgId);

    @Select("SELECT COUNT(*) FROM message_read WHERE msg_id = #{msgId}")
    long countByMsgId(@Param("msgId") Long msgId);

    @Insert("INSERT IGNORE INTO message_read (id, msg_id, uid, read_at) VALUES (#{id}, #{msgId}, #{uid}, #{readAt})")
    int insertIgnore(MessageRead messageRead);
}