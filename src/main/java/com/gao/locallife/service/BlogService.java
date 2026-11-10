package com.gao.locallife.service;

import com.gao.locallife.dto.FeedResult;
import com.gao.locallife.entity.Blog;

import java.util.List;

/** 探店笔记：发布、点赞、Feed 流。 */
public interface BlogService {

    /** 发布笔记，并推送到所有粉丝的 Feed（推模式 / 写扩散）。 */
    Long publish(Blog blog);

    /**
     * 点赞 / 取消点赞（同一个接口切换）。
     *
     * @return true 点赞，false 取消
     */
    boolean toggleLike(Long blogId);

    /** 点赞排行（按时间倒序取最近的点赞用户）。 */
    List<Long> likeTop(Long blogId, Integer current);

    /**
     * 我的 Feed（时间戳游标滚动分页，不是 offset 翻页）。
     *
     * @param lastTime 上一页最后一条的时间戳，首次传 null
     * @param offset   同一时间戳已消费的条数，首次传 0
     */
    FeedResult feed(Long lastTime, Integer offset);

    /** 某个商户下的笔记（普通 DB 分页，和 Feed 的游标分页对比用）。 */
    List<Blog> byShop(Long shopId, Integer current);
}
