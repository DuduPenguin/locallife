package com.gao.locallife.dto;

import com.gao.locallife.entity.Blog;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Feed 分页结果。
 *
 * <p>用「时间戳游标 + 同分偏移」而不是页码：深分页不会越翻越慢，
 * 新数据插入也不会导致重复或漏读。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeedResult {

    private List<Blog> list;

    /** 本次返回的最后一条的时间戳，作为下一页的 lastTime */
    private Long minTime;

    /** 与 minTime 相同的记录已经消费了几条，作为下一页的 offset */
    private Integer offset;
}
