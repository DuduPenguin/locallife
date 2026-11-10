package com.gao.locallife.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 优惠券（表 tb_voucher）。type：0 普通券，1 秒杀券。 */
@Data
public class Voucher implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long shopId;
    private String title;
    private String subTitle;
    private String rules;
    /** 支付金额，单位：分 */
    private Long payValue;
    /** 抵扣金额，单位：分 */
    private Long actualValue;
    private Integer type;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
