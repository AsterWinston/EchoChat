package me.aster.echochat.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.aster.echochat.common.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * MyBatis-Plus 用户表的增删查映射器。
 * @author AsterWinston
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 根据账户标识（UID 或邮箱）查找用户。
     *
     * @param account 账户标识字符串
     * @return 匹配的用户，若不存在则返回 null
     */
    @Select("SELECT * FROM user WHERE uid = #{account} OR email = #{account}")
    User findByAccount(@Param("account") String account);

    /**
     * 搜索未删除的用户，按 UID、邮箱和昵称匹配关键词。
     *
     * @param keyword 搜索关键词
     * @return 匹配的用户列表，最多 20 条
     */
    @Select("SELECT * FROM user WHERE is_deleted = 0 AND (" +
            "uid LIKE CONCAT('%', #{keyword}, '%') OR " +
            "email LIKE CONCAT('%', #{keyword}, '%') OR " +
            "nickname LIKE CONCAT('%', #{keyword}, '%')) " +
            "LIMIT 20")
    List<User> searchByKeyword(@Param("keyword") String keyword);
}
