package me.aster.echochat.moment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.aster.echochat.moment.entity.Moment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * moment表的MyBatis映射器，提供CRUD及自定义时间线查询。
 * @author AsterWinston
 */
@Mapper
public interface MomentMapper extends BaseMapper<Moment> {

    /**
     * @param uid        发布者UID
     * @param beforeTime 基于时间的分页游标
     * @param limit      最大结果数
     * @return 该用户的分页活跃动态，最新优先
     */
    @Select("SELECT * FROM moment WHERE uid = #{uid} AND is_deleted = 0 " +
            "AND created_at < #{beforeTime} ORDER BY created_at DESC LIMIT #{limit}")
    List<Moment> findByUidPaginated(@Param("uid") Long uid,
                                    @Param("beforeTime") java.time.LocalDateTime beforeTime,
                                    @Param("limit") int limit);

    /**
     * @param momentId 动态ID
     * @return 活跃的动态，未找到或已删除则返回null
     */
    @Select("SELECT * FROM moment WHERE moment_id = #{momentId} AND is_deleted = 0")
    Moment findActiveById(@Param("momentId") Long momentId);
}
