package me.aster.echochat.group.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.aster.echochat.group.entity.GroupInvite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * group_invite表的MyBatis-Plus映射器。
 * @author AsterWinston
 */
@Mapper
public interface GroupInviteMapper extends BaseMapper<GroupInvite> {

    /**
     * 根据邀请码查询邀请记录。
     *
     * @param code 邀请码
     * @return 匹配的 {@link GroupInvite}，不存在则返回null
     */
    @Select("SELECT * FROM group_invite WHERE code = #{code}")
    GroupInvite findByCode(@Param("code") String code);
}
