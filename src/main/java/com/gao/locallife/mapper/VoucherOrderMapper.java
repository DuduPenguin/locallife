package com.gao.locallife.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gao.locallife.entity.VoucherOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 优惠券订单 Mapper。 */
@Mapper
public interface VoucherOrderMapper extends BaseMapper<VoucherOrder> {

    /** 按用户 + 券查订单数（一人一单的第一层判断，最终靠唯一索引兜底）。 */
    @Select("SELECT COUNT(*) FROM tb_voucher_order WHERE user_id = #{userId} AND voucher_id = #{voucherId}")
    int countByUserAndVoucher(@Param("userId") Long userId, @Param("voucherId") Long voucherId);

    /**
     * 状态机取消：只有"待支付(1)"能改成"已取消(4)"。
     *
     * <p>用 UPDATE 带状态条件，天然幂等 —— 重复调用第二次返回 0 行。
     */
    @Update("UPDATE tb_voucher_order SET status = 4 WHERE id = #{orderId} AND status = 1")
    int cancelIfPending(@Param("orderId") Long orderId);

    /**
     * 状态机支付：只有"待支付(1)"能改成"已支付(2)"。
     *
     * <p>重复支付返回 0 行，不会把已取消的订单改成已支付。
     */
    @Update("UPDATE tb_voucher_order SET status = 2, pay_time = NOW() " +
            "WHERE id = #{orderId} AND status = 1")
    int payIfPending(@Param("orderId") Long orderId);

    /** 该券的有效订单数（待支付 + 已支付 + 已核销），对账用。 */
    @Select("SELECT COUNT(*) FROM tb_voucher_order WHERE voucher_id = #{voucherId} AND status IN (1, 2, 3)")
    int countValidByVoucher(@Param("voucherId") Long voucherId);
}
