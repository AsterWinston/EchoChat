package me.aster.echochat.group.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.aster.echochat.group.entity.GroupJoinRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * {@link GroupJoinRequest}的MyBatis-Plus映射器，提供待处理请求和用户在群组中最新申请的查询。
 * @author AsterWinston
 */
@Mapper
public interface GroupJoinRequestMapper extends BaseMapper<GroupJoinRequest> {

    @Select("SELECT * FROM group_join_request WHERE gid = #{gid} AND status = 'pending' ORDER BY created_at ASC")
    List<GroupJoinRequest> findPendingByGid(@Param("gid") Long gid);

    @Select("SELECT * FROM group_join_request WHERE gid = #{gid} AND uid = #{uid} ORDER BY created_at DESC LIMIT 1")
    GroupJoinRequest findByGidAndUid(@Param("gid") Long gid, @Param("uid") Long uid);
}
