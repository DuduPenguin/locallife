package com.gao.locallife.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gao.locallife.entity.SeckillVoucher;
import org.apache.ibatis.annotations.Mapper;

/** 秒杀券库存 Mapper：单表 CRUD 由 BaseMapper 提供，复杂 SQL 后续在对应模块加。 */
@Mapper
public interface SeckillVoucherMapper extends BaseMapper<SeckillVoucher> {
}
