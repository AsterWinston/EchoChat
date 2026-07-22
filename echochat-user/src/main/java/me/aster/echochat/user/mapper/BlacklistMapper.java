package me.aster.echochat.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.aster.echochat.user.entity.Blacklist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * MyBatis-Plus 黑名单表的增删查映射器。
 * @author AsterWinston
 */
@Mapper
public interface BlacklistMapper extends BaseMapper<Blacklist> {

    /**
     * 查找由指定用户创建的所有黑名单条目。
     *
     * @param uid 用户 ID
     * @return 黑名单条目列表
     */
    @Select("SELECT * FROM blacklist WHERE uid = #{uid}")
    List<Blacklist> findByUid(@Param("uid") Long uid);

    /**
     * 查找特定用户与特定被屏蔽用户之间的黑名单条目。
     *
     * @param uid        用户 ID
     * @param blockedUid 被屏蔽用户 ID
     * @return 黑名单条目，若不存在则返回 null
     */
    @Select("SELECT * FROM blacklist WHERE uid = #{uid} AND blocked_uid = #{blockedUid}")
    Blacklist findByUidAndBlockedUid(@Param("uid") Long uid, @Param("blockedUid") Long blockedUid);
}
