package me.aster.echochat.moment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.aster.echochat.moment.entity.MomentLike;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;
/**
 * moment_like表的MyBatis映射器。
 * @author AsterWinston
 */
@Mapper
public interface MomentLikeMapper extends BaseMapper<MomentLike> {

    /**
     * 查询指定动态的所有点赞记录。
     *
     * @param momentId 动态ID
     * @return 该动态的所有点赞，按时间升序排列
     */
    @Select("SELECT * FROM moment_like WHERE moment_id = #{momentId} ORDER BY created_at ASC")
    List<MomentLike> findByMomentId(@Param("momentId") Long momentId);

    /**
     * 查询用户对指定动态的点赞记录。
     *
     * @param momentId 动态ID
     * @param uid      用户ID
     * @return 点赞记录，未点赞则返回null
     */
    @Select("SELECT * FROM moment_like WHERE moment_id = #{momentId} AND uid = #{uid}")
    MomentLike findByMomentAndUid(@Param("momentId") Long momentId, @Param("uid") Long uid);

    /**
     * 根据动态ID删除所有点赞。
     *
     * @param momentId 动态ID
     * @return 删除的行数
     */
    @Delete("DELETE FROM moment_like WHERE moment_id = #{momentId}")
    int deleteByMomentId(@Param("momentId") Long momentId);

    /**
     * 查询用户已点赞的动态ID列表。
     *
     * @param uid       用户ID
     * @param momentIds 动态ID列表
     * @return 用户已点赞的动态ID
     */
    @Select("<script>SELECT moment_id FROM moment_like WHERE uid = #{uid} AND moment_id IN <foreach item='id' collection='momentIds' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Long> findLikedMomentIds(@Param("uid") Long uid, @Param("momentIds") List<Long> momentIds);

    /**
     * 批量查询动态的点赞数。
     *
     * @param momentIds 动态ID列表
     * @return 每个动态的点赞数（moment_id, cnt）
     */
    @Select("<script>SELECT moment_id, COUNT(*) as cnt FROM moment_like WHERE moment_id IN <foreach item='id' collection='momentIds' open='(' separator=',' close=')'>#{id}</foreach> GROUP BY moment_id</script>")
    List<Map<String, Object>> countByMomentIds(@Param("momentIds") List<Long> momentIds);
}
