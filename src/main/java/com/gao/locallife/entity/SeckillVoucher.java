package com.gao.locallife.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 秒杀券库存（表 tb_seckill_voucher）。
 *
 * <p>单独拆表的原因：秒杀时这张表会被高频 UPDATE（stock = stock - 1），
 * 拆出来行锁只锁这张窄表，不和 tb_voucher 的其他更新互相干扰。
 */
@Data
public class SeckillVoucher implements Serializable {

    @TableId(type = IdType.INPUT)
    private Long voucherId;
    private Integer stock;
    private LocalDateTime beginTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
