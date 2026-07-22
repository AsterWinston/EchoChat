package me.aster.echochat.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.aster.echochat.message.entity.OfflineMessage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 离线消息持久化的MyBatis映射器。
 * @author AsterWinston
 */
@Mapper
public interface OfflineMessageMapper extends BaseMapper<OfflineMessage> {

    /**
     * 查询指定用户的所有离线消息，按序号升序排列。
     *
     * @param uid 用户ID
     * @return 该用户的所有离线消息，按序号升序排列
     */
    @Select("SELECT * FROM offline_message WHERE uid = #{uid} ORDER BY seq ASC")
    List<OfflineMessage> findByUid(@Param("uid") Long uid);

    /**
     * 同步成功后删除该用户的所有离线消息。
     *
     * @param uid 用户ID
     * @return 删除的记录数
     */
    @Delete("DELETE FROM offline_message WHERE uid = #{uid}")
    int deleteByUid(@Param("uid") Long uid);

    /**
     * 插入离线消息，在(uid, msg_id)唯一键冲突时静默跳过，
     * 确保ACK重试和首次推送降级不会存储重复消息。
     *
     * @param offline 离线消息记录
     * @return 插入的行数（记录已存在时返回0）
     */
    @Insert("INSERT IGNORE INTO offline_message (id, uid, msg_id, seq, created_at) " +
            "VALUES (#{id}, #{uid}, #{msgId}, #{seq}, #{createdAt})")
    int insertIgnore(OfflineMessage offline);
}