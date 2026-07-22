package me.aster.echochat.moment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.aster.echochat.moment.entity.MomentPrivacy;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * moment_privacy表的MyBatis映射器。
 * @author AsterWinston
 */
@Mapper
public interface MomentPrivacyMapper extends BaseMapper<MomentPrivacy> {

    /**
     * 查询指定动态中被屏蔽的UID列表。
     *
     * @param momentId 动态ID
     * @return 被禁止查看该动态的UID列表
     */
    @Select("SELECT block_uid FROM moment_privacy WHERE moment_id = #{momentId}")
    List<Long> findBlockUidsByMomentId(@Param("momentId") Long momentId);

    /**
     * 根据动态ID删除所有隐私屏蔽记录。
     *
     * @param momentId 动态ID
     * @return 删除的行数
     */
    @Delete("DELETE FROM moment_privacy WHERE moment_id = #{momentId}")
    int deleteByMomentId(@Param("momentId") Long momentId);

    /**
     * 批量查询动态对应的屏蔽UID列表。
     *
     * @param momentIds 动态ID列表
     * @return 每个动态的屏蔽记录（moment_id, block_uid）
     */
    @Select("<script>SELECT moment_id, block_uid FROM moment_privacy WHERE moment_id IN <foreach item='id' collection='momentIds' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Map<String, Object>> findBlockUidsByMomentIds(@Param("momentIds") List<Long> momentIds);
}
