package com.gao.locallife.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gao.locallife.entity.SeckillVoucher;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 秒杀券库存 Mapper。 */
@Mapper
public interface SeckillVoucherMapper extends BaseMapper<SeckillVoucher> {

    /**
     * 条件更新扣库存 —— <b>最后一道物理防线</b>。
     *
     * <p>为什么是 {@code stock = stock - 1} 而不是 {@code SET stock = #{新值}}：
     * 后者两个事务都读到 10、都写 9，卖 2 件只扣 1 件；
     * 前者是"在当前值上减"，MySQL 对命中行加行锁，并发事务串行执行。
     *
     * <p>{@code AND stock > 0} 保证物理上不会扣成负数。
     *
     * @return 影响行数：1 成功，0 表示库存不足
     */
    @Update("UPDATE tb_seckill_voucher SET stock = stock - 1 " +
            "WHERE voucher_id = #{voucherId} AND stock > 0")
    int deductStock(@Param("voucherId") Long voucherId);

    /** 回补库存（取消订单 / 对账补偿用）。 */
    @Update("UPDATE tb_seckill_voucher SET stock = stock + #{count} WHERE voucher_id = #{voucherId}")
    int increaseStock(@Param("voucherId") Long voucherId, @Param("count") int count);

    /**
     * ⚠️ <b>仅用于复现"超卖"</b>：没有 {@code AND stock > 0} 守卫。
     *
     * <p>并发下会扣成负数 —— M4-A 故意用它压测出对比数据，M4-B 之后不再调用。
     */
    @Update("UPDATE tb_seckill_voucher SET stock = stock - 1 WHERE voucher_id = #{voucherId}")
    int deductStockUnsafe(@Param("voucherId") Long voucherId);
}
