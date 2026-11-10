package com.gao.locallife.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gao.locallife.entity.VoucherOrder;
import org.apache.ibatis.annotations.Mapper;

/** 优惠券订单 Mapper：单表 CRUD 由 BaseMapper 提供，复杂 SQL 后续在对应模块加。 */
@Mapper
public interface VoucherOrderMapper extends BaseMapper<VoucherOrder> {
}
