package com.gao.locallife.service;

import java.util.List;

/** 关注关系（两个 Set：我关注谁 / 谁关注我）。 */
public interface FollowService {

    /** 关注，同时维护粉丝集合与被关注者的粉丝数。 */
    void follow(Long followUserId);

    /** 取关。 */
    void unfollow(Long followUserId);

    /** 是否已关注。 */
    boolean isFollowed(Long followUserId);

    /**
     * 共同关注：两个 Set 求交集（SINTER）。
     *
     * <p>这也是"为什么关注要存两份"的原因——只存一份的话求交集要遍历所有人。
     */
    List<Long> commonFollows(Long otherUserId);
}
