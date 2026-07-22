package me.aster.echochat.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.aster.echochat.message.entity.MessageDeletion;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@link MessageDeletion}的MyBatis-Plus映射器，提供用户级别消息删除记录的CRUD操作。
 * @author AsterWinston
 */
@Mapper
public interface MessageDeletionMapper extends BaseMapper<MessageDeletion> {
}