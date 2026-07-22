package me.aster.echochat.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用分页请求DTO，包含页码和每页大小。
 * @author AsterWinston
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageRequest {

    /** 当前页码，从1开始 */
    private int page = 1;
    /** 每页记录数 */
    private int size = 20;

    /**
     * @return 根据页码和每页大小计算出的数据库级偏移量
     */
    public int getOffset() {
        return (page - 1) * size;
    }
}