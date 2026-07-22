package me.aster.echochat.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.aster.echochat.notification.entity.Notification;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * {@code notification}表的MyBatis-Plus映射器。
 * 继承{@link BaseMapper}获得标准CRUD能力，并添加自定义查询方法。
 * @author AsterWinston
 */
public interface NotificationMapper extends BaseMapper<Notification> {

    /**
     * 查找某用户的通知，按创建时间倒序排列。
     *
     * @param uid   目标用户ID
     * @param limit 最多返回的通知数
     * @return 通知列表
     */
    @Select("SELECT * FROM notification WHERE uid = #{uid} ORDER BY created_at DESC LIMIT #{limit}")
    List<Notification> findByUid(@Param("uid") Long uid, @Param("limit") int limit);

    /**
     * 统计用户的未读通知数量。
     *
     * @param uid 目标用户ID
     * @return 未读通知数
     */
    @Select("SELECT COUNT(*) FROM notification WHERE uid = #{uid} AND is_read = 0")
    int countUnread(@Param("uid") Long uid);

    /**
     * 将用户的所有未读通知标记为已读。
     *
     * @param uid 目标用户ID
     * @return 更新的行数
     */
    @Update("UPDATE notification SET is_read = 1 WHERE uid = #{uid} AND is_read = 0")
    int markAllRead(@Param("uid") Long uid);
}
