package me.aster.echochat.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.aster.echochat.user.entity.FriendRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * MyBatis-Plus 好友请求表的增删查映射器。
 * @author AsterWinston
 */
@Mapper
public interface FriendRequestMapper extends BaseMapper<FriendRequest> {

    /**
     * 查找指定用户收到的所有好友请求。
     *
     * @param toUid 接收方用户 ID
     * @return 收到的请求列表，按创建时间降序排列
     */
    @Select("SELECT * FROM friend_request WHERE to_uid = #{toUid} ORDER BY created_at DESC")
    List<FriendRequest> findReceivedRequests(@Param("toUid") Long toUid);

    /**
     * 按状态过滤已收到的好友请求。
     *
     * @param toUid  接收方用户 ID
     * @param status 请求状态过滤条件
     * @return 匹配的已收到请求列表
     */
    @Select("SELECT * FROM friend_request WHERE to_uid = #{toUid} AND status = #{status} ORDER BY created_at DESC")
    List<FriendRequest> findReceivedRequestsByStatus(@Param("toUid") Long toUid, @Param("status") String status);

    /**
     * 查找指定用户发出的所有好友请求。
     *
     * @param fromUid 发送方用户 ID
     * @return 发出的请求列表，按创建时间降序排列
     */
    @Select("SELECT * FROM friend_request WHERE from_uid = #{fromUid} ORDER BY created_at DESC")
    List<FriendRequest> findSentRequests(@Param("fromUid") Long fromUid);

    /**
     * 按状态过滤已发出的好友请求。
     *
     * @param fromUid 发送方用户 ID
     * @param status  请求状态过滤条件
     * @return 匹配的已发出请求列表
     */
    @Select("SELECT * FROM friend_request WHERE from_uid = #{fromUid} AND status = #{status} ORDER BY created_at DESC")
    List<FriendRequest> findSentRequestsByStatus(@Param("fromUid") Long fromUid, @Param("status") String status);

    /**
     * 查找两个用户之间未过期的待处理请求。
     *
     * @param fromUid 发送方用户 ID
     * @param toUid   接收方用户 ID
     * @return 待处理的请求，若不存在则返回 null
     */
    @Select("SELECT * FROM friend_request WHERE from_uid = #{fromUid} AND to_uid = #{toUid} AND status = 'pending' AND expire_at > NOW()")
    FriendRequest findPendingRequest(@Param("fromUid") Long fromUid, @Param("toUid") Long toUid);

    /**
     * 使用 CAS（比较并交换）方式更新请求状态，确保并发安全。
     *
     * @param requestId      请求 ID
     * @param newStatus      新状态
     * @param expectedStatus 期望的当前状态
     * @return 受影响的行数（0 表示状态已被其他线程修改）
     */
    @Update("UPDATE friend_request SET status = #{newStatus}, handled_at = NOW() WHERE id = #{requestId} AND status = #{expectedStatus}")
    int updateStatus(@Param("requestId") Long requestId, @Param("newStatus") String newStatus, @Param("expectedStatus") String expectedStatus);
}
