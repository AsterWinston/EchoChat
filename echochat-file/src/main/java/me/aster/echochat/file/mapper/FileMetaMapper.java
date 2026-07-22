package me.aster.echochat.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.aster.echochat.file.entity.FileMeta;
import org.apache.ibatis.annotations.Mapper;

/**
 * file_meta表的MyBatis-Plus映射器。
 * @author AsterWinston
 */
@Mapper
public interface FileMetaMapper extends BaseMapper<FileMeta> {
}
