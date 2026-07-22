package me.aster.echochat.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Collections;
import java.util.List;

/**
 * 通用分页结果DTO，包含分页元数据和当前页的记录列表。
 *
 * @param <T> 本页记录的类型
 * @author AsterWinston
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** 当前页码 */
    private int page;
    /** 每页大小 */
    private int size;
    /** 所有页的记录总数 */
    private long total;
    /** 总页数 */
    private int pages;
    /** 当前页的记录列表 */
    private List<T> records;

    /**
     * 创建带有计算好的页数的分页结果。
     *
     * @param page    当前页码
     * @param size    每页大小
     * @param total   总记录数
     * @param records 当前页的记录
     * @param <T>     记录类型
     * @return 填充好的 {@link PageResult}
     */
    public static <T> PageResult<T> of(int page, int size, long total, List<T> records) {
        int pages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
        return new PageResult<>(page, size, total, pages, records);
    }

    /**
     * 创建空分页结果。
     *
     * @param page 当前页码
     * @param size 每页大小
     * @param <T>  记录类型
     * @return 总数为0且记录列表为空的空 {@link PageResult}
     */
    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(page, size, 0, 0, Collections.emptyList());
    }
}