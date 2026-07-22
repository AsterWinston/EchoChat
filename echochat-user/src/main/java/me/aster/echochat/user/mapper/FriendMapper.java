package me.aster.echochat.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.aster.echochat.user.entity.Friend;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * MyBatis-Plus 好友表的增删查映射器。
 * @author AsterWinston
 */
@Mapper
public interface FriendMapper extends BaseMapper<Friend> {

    /**
     * 查找指定用户拥有的所有好友记录。
     *
     * @param uid 用户 ID
     * @return 好友关系列表
     */
    @Select("SELECT * FROM friend WHERE user_uid = #{uid}")
    List<Friend> findByUid(@Param("uid") Long uid);

    /**
     * 获取用户的所有好友 UID 列表。
     *
     * @param uid 用户 ID
     * @return 好友 UID 列表
     */
    @Select("SELECT friend_uid FROM friend WHERE user_uid = #{uid}")
    List<Long> findFriendUids(@Param("uid") Long uid);
}
