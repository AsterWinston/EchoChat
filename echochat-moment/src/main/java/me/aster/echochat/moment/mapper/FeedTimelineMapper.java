package me.aster.echochat.moment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.aster.echochat.moment.entity.FeedTimeline;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * feed_timeline表（写扩散模式的时间线收件箱）的MyBatis映射器。
 * @author AsterWinston
 */
@Mapper
public interface FeedTimelineMapper extends BaseMapper<FeedTimeline> {

    /**
     * @param ownerUid   时间线所有者UID
     * @param beforeTime 基于时间的分页游标
     * @param limit      最大结果数
     * @return 分页的时间线条目，最新优先
     */
    @Select("SELECT * FROM feed_timeline WHERE owner_uid = #{ownerUid} AND created_at < #{beforeTime} " +
            "ORDER BY created_at DESC LIMIT #{limit}")
    List<FeedTimeline> findByOwner(@Param("ownerUid") Long ownerUid,
                                   @Param("beforeTime") java.time.LocalDateTime beforeTime,
                                   @Param("limit") int limit);

    /**
     * @param momentId 动态ID
     * @return 删除的行数
     */
    @Delete("DELETE FROM feed_timeline WHERE moment_id = #{momentId}")
    int deleteByMomentId(@Param("momentId") Long momentId);

}
