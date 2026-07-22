package me.aster.echochat.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.aster.echochat.message.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 收藏持久化的MyBatis映射器，提供用户范围检索的自定义查询。
 * @author AsterWinston
 */
@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {

    /**
     * 查找用户的所有收藏，按收藏时间降序排列。
     *
     * @param uid 用户ID
     * @return {@link Favorite}记录列表
     */
    @Select("SELECT * FROM favorite WHERE uid = #{uid} ORDER BY collected_at DESC")
    List<Favorite> findByUid(@Param("uid") Long uid);

    /**
     * 按消息类型筛选用户的收藏，按收藏时间降序排列。
     *
     * @param uid     用户ID
     * @param msgType 消息内容类型
     * @return 匹配的{@link Favorite}记录列表
     */
    @Select("SELECT * FROM favorite WHERE uid = #{uid} AND msg_type = #{msgType} ORDER BY collected_at DESC")
    List<Favorite> findByUidAndMsgType(@Param("uid") Long uid, @Param("msgType") String msgType);

    /**
     * 按用户和消息ID查找特定收藏。
     *
     * @param uid   用户ID
     * @param msgId 消息ID
     * @return 匹配的{@link Favorite}，或null
     */
    @Select("SELECT * FROM favorite WHERE uid = #{uid} AND msg_id = #{msgId}")
    Favorite findByUidAndMsgId(@Param("uid") Long uid, @Param("msgId") Long msgId);
}