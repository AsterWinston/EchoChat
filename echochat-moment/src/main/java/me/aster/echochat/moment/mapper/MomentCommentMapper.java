package me.aster.echochat.moment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.aster.echochat.moment.entity.MomentComment;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * moment_comment表的MyBatis映射器。
 * @author AsterWinston
 */
@Mapper
public interface MomentCommentMapper extends BaseMapper<MomentComment> {

    /**
     * @param momentId 动态ID
     * @return 该动态的所有评论，按时间升序排列
     */
    @Select("SELECT * FROM moment_comment WHERE moment_id = #{momentId} ORDER BY created_at ASC")
    List<MomentComment> findByMomentId(@Param("momentId") Long momentId);

    /**
     * @param momentId 动态ID
     * @return 删除的行数
     */
    @Delete("DELETE FROM moment_comment WHERE moment_id = #{momentId}")
    int deleteByMomentId(@Param("momentId") Long momentId);

    @Select("<script>SELECT moment_id, COUNT(*) as cnt FROM moment_comment WHERE moment_id IN <foreach item='id' collection='momentIds' open='(' separator=',' close=')'>#{id}</foreach> GROUP BY moment_id</script>")
    List<Map<String, Object>> countByMomentIds(@Param("momentIds") List<Long> momentIds);
}
