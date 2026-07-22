package me.aster.echochat.group.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.aster.echochat.group.entity.GroupInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

/**
 * group_info表的MyBatis-Plus映射器。
 * @author AsterWinston
 */
@Mapper
public interface GroupInfoMapper extends BaseMapper<GroupInfo> {

    /**
     * @param uid 所有者的用户ID
     * @return 用户拥有的群组列表
     */
    @Select("SELECT * FROM group_info WHERE owner_uid = #{uid}")
    List<GroupInfo> findByOwnerUid(@Param("uid") Long uid);

    /**
     * @param uid 成员的用户ID
     * @return 用户已加入的群组列表
     */
    @Select("SELECT gi.* FROM group_info gi " +
            "INNER JOIN group_member gm ON gi.gid = gm.gid " +
            "WHERE gm.uid = #{uid}")
    List<GroupInfo> findByMemberUid(@Param("uid") Long uid);

    @Select("SELECT * FROM group_info WHERE name LIKE CONCAT('%', #{keyword}, '%')")
    List<GroupInfo> searchByName(@Param("keyword") String keyword);
}
