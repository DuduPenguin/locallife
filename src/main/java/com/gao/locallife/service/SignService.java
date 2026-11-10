package com.gao.locallife.service;

/** 签到：Bitmap 存储。 */
public interface SignService {

    /**
     * 今日签到。
     *
     * @return true 本次是新的签到；false 今天已经签过
     */
    boolean sign();

    /** 本月签到天数（BITCOUNT）。 */
    long countThisMonth();

    /**
     * 截至今天的连续签到天数。
     *
     * <p>用 BITFIELD 一次取回整月记录，再从今天往前数——比逐位 GETBIT 快得多。
     */
    long continuousDays();
}
