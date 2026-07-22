package me.aster.echochat.group.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.aster.echochat.group.entity.GroupMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * group_member表的MyBatis-Plus映射器。
 * @author AsterWinston
 */
@Mapper
public interface GroupMemberMapper extends BaseMapper<GroupMember> {

    /**
     * 根据群组ID和用户ID查询成员记录。
     *
     * @param gid 群组ID
     * @param uid 用户ID
     * @return {@link GroupMember}，非成员则返回null
     */
    @Select("SELECT * FROM group_member WHERE gid = #{gid} AND uid = #{uid}")
    GroupMember findByGidAndUid(@Param("gid") Long gid, @Param("uid") Long uid);

    /**
     * 查询指定群组中所有成员的UID列表。
     *
     * @param gid 群组ID
     * @return 群组中的用户ID列表
     */
    @Select("SELECT uid FROM group_member WHERE gid = #{gid}")
    List<Long> findUidsByGid(@Param("gid") Long gid);

    /**
     * 查询指定群组中的所有成员记录。
     *
     * @param gid 群组ID
     * @return 群组中的所有成员列表
     */
    @Select("SELECT * FROM group_member WHERE gid = #{gid}")
    List<GroupMember> findByGid(@Param("gid") Long gid);

    /**
     * 统计指定群组中的成员总数。
     *
     * @param gid 群组ID
     * @return 群组中的成员数量
     */
    @Select("SELECT COUNT(*) FROM group_member WHERE gid = #{gid}")
    int countByGid(@Param("gid") Long gid);
}
