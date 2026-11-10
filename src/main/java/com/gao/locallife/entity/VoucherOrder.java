package com.gao.locallife.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 优惠券订单（表 tb_voucher_order）。
 *
 * <p>主键由应用生成（时间戳左移 32 位 | Redis 自增），所以是 IdType.INPUT，不是自增。
 */
@Data
public class VoucherOrder implements Serializable {

    @TableId(type = IdType.INPUT)
    private Long id;
    private Long userId;
    private Long voucherId;
    private Long payValue;
    private Long actualValue;

    /** 1 待支付 2 已支付 3 已核销 4 已取消 5 退款中 6 已退款 */
    private Integer status;
    /** 有效标记：1 有效，null 已取消（唯一索引对 NULL 不去重，所以取消后能重新下单） */
    private Integer activeFlag;

    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime useTime;
    private LocalDateTime updateTime;
}
